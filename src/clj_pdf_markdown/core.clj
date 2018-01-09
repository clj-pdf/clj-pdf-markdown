(ns clj-pdf-markdown.core
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
   [clojure.data.xml :as xml])
  (:import org.commonmark.parser.Parser))


;;;; CONFIG

(def default-pdf-config
  {:anchor   {}
   :heading  {:h1 {:style {:size 16}}
              :h2 {:style {:size 15}}
              :h3 {:style {:size 14}}
              :h4 {:style {:size 13}}
              :h5 {:style {:size 12}}
              :h6 {:style {:size 11}}}
   :image     {}
   :line      {}
   :list      {:ol {:numbered true}
               :ul {:symbol   "• "}}
   :paragraph {}
   :spacer    {:allow-extra-line-breaks? true
               :single-value 0
               :extra-starting-value 0}
   :wrap {:unwrap-singleton? true
          :global-wrapper :vector ;; :paragraph or :vector 
          }})

;;;; COMMONMARK HELPERS

(defn parse-markdown [s]
  (let [parser (.build (Parser/builder))]
    (.parse parser s)))

(defn commonmark-node->class-k [obj]
  (-> obj class .getName (str/replace #"org.commonmark.node." "") keyword))

;;; Node methods

(defn- node-destination [node] (-> node bean :destination))

(defn- node-parent [node] (-> node bean :parent))

(defn- node-parent-class-k [node] 
  (-> node node-parent commonmark-node->class-k))

(defn- node-title [node] (-> node bean :title))

(defn- node-level
  [node] 
  (->> node bean :level (str "h") keyword))

(defn node-has-single-child? [node]
  (-> node .getFirstChild .getNext not))

(defn node-is-singleton? [node]
   (not (or (.getNext node) (.getPrevious node))))

(defn node-children
  "Returns a seq of the children of a commonmark-java AST node."
  [node]
  (into [] (take-while some? (iterate #(.getNext %) (.getFirstChild node)))))

(defn- node-text-children
  "Recursively walks over the given commonmark-java AST node depth-first,
   extracting and concatenating literals from any text nodes it visits."
  [node]
  (->> (tree-seq (constantly true) node-children node)
       (filter #(instance? org.commonmark.node.Text %))
       (map #(.getLiteral %))
       (apply str)))

;;; Node predicates

(defn get-offset-config [pdf-config]
  (-> pdf-config :image (select-keys [:x :y])))


(defn singleton? [pdf-config node]
  (if (and (-> pdf-config :wrap :unwrap-singleton?) 
           (node-has-single-child? node))
    :singleton
    :non-singleton))

(defn node-alone? [pdf-config node result]
   (if (and (-> pdf-config :wrap :unwrap-singleton?) 
            (node-is-singleton? node)
            #_(= (node-parent-class-k node) :Document)
            (= (count result) 1))
    :alone
    :not-alone))

(defn image-alone? [pdf-config node]
  (if (and (empty? (get-offset-config pdf-config))
           (-> pdf-config :wrap :unwrap-singleton?)
           (-> node node-is-singleton?)
           (-> node node-parent node-is-singleton?)
           (= :Document (-> node node-parent node-parent-class-k)))
    :alone
    :not-alone))

;;; Node wrapers

(defmulti wrap-document
  (fn [pdf-config node result]
    [(-> pdf-config :wrap :global-wrapper) 
     (singleton? pdf-config node)]))

(defmethod wrap-document [:paragraph :singleton] [pdf-config node result]
  (into [:paragraph (:paragraph pdf-config)] result))

(defmethod wrap-document [:paragraph :non-singleton] [pdf-config node result]
  (into [:paragraph (:paragraph pdf-config)] result))

(defmethod wrap-document [:vector :singleton] [pdf-config node result]
  (first result))

(defmethod wrap-document :default [pdf-config node result] 
  result)

(defmulti wrap-paragraph 
  (fn [pdf-config node result] (node-alone? pdf-config node result)))

(defmethod wrap-paragraph :alone [pdf-config _ result]
  (first result))

(defmethod wrap-paragraph :not-alone [pdf-config _ result]
  (into [:paragraph (:paragraph pdf-config)] result))

(defmulti wrap-image
  (fn [pdf-config node result] 
    (image-alone? pdf-config node)))

(defmethod wrap-image :alone
  [pdf-config node result]
  result)

(defmethod wrap-image :default [pdf-config _ result]
  [:chunk (get-offset-config pdf-config) result])

;;; Html node processors

(defmulti html-tag->clj-pdf 
  (fn [pdf-config s] (-> s xml/parse-str :tag)))

(defmethod html-tag->clj-pdf :br [pdf-config s]
  (let [n-str (-> s xml/parse-str :attrs :n)
        n (or (when n-str (Integer/parseInt n-str)) 1)] 
    [:spacer (-> pdf-config :spacer :extra-starting-value (+ n) dec)]))

(defmethod html-tag->clj-pdf :default [pdf-config s] s)



;;;; RENDERERS

;;; Children renderer

(declare render)

(defmulti render-children (fn [pdf-config node] 
                            (commonmark-node->class-k node)))
(defn render-children* [pdf-config node] 
  (mapv (partial render pdf-config) (node-children node)))


;;; Main renderer

(def render-derivals
  {:LineBreak [:HardLineBreak :SoftLineBreak]
   :Literal   [:BlockQuote :FencedCodeBlock :IndentedCodeBlock :Code]})

(defn make-hierarchy-from-derivals [derivals]
  (reduce (fn [hier [child parent]] (derive hier child parent)) 
          (make-hierarchy)
          (for [[parent children] derivals 
                child children] 
            [child parent])))

(def render-hierarchy (atom (make-hierarchy-from-derivals render-derivals)))


;; TODO
;; show render methods as a declarative parsing tree:
#_{:Document {:paragraph [:paragraph :args]
              :vector []}
   :Paragraph {:wrapper [:paragraph :args]}}

(defmulti render 
  (fn [pdf-config node] 
    (do (println (str "bean: " (bean node))) 
        (commonmark-node->class-k node)))
  :hierarchy render-hierarchy)

(defmethod render :Document [pdf-config node] 
  (->> node
       (render-children* pdf-config)
       (wrap-document pdf-config node)))

(defmethod render :Heading [pdf-config node] 
  (into 
   [:heading (get-in pdf-config [:heading (node-level node)])]
   (render-children* pdf-config node)))

(defmethod render :Paragraph [pdf-config node]
  (->> node
       (render-children* pdf-config)
       (wrap-paragraph pdf-config node)))

(defmethod render :Text [pdf-config node]
  (.getLiteral node))

(defmethod render :BulletList [pdf-config node]
  (into [:list (get-in pdf-config [:list :ul])]
        (render-children* pdf-config node)))

(defmethod render :OrderedList [pdf-config node]  
  (into [:list (get-in pdf-config [:list :ol])] 
        (render-children* pdf-config node)))

(defmethod render :ListItem [pdf-config node]
  (->> node (render-children* pdf-config) first))

(defmethod render :Link [pdf-config node] 
  (into [:anchor (merge (:anchor pdf-config) {:target (node-destination node)})]
        (render-children* pdf-config node)))

(defmethod render :Image [pdf-config node]
  (let [annotation [(node-title node) (node-text-children node)]
        config (:image pdf-config)
        offset-config (select-keys config [:x :y])
        image-config (-> config 
                         (dissoc :x :y)
                         (merge {:annotation annotation}))
        image-element [:image image-config (node-destination node)]]
    (wrap-image pdf-config node image-element)))

(defmethod render :Emphasis [pdf-config node]
  (into [:phrase {:style :italic}] 
        (render-children* pdf-config node)))

(defmethod render :StrongEmphasis [pdf-config node] 
  (into [:phrase {:style :bold}]
        (render-children* pdf-config node)))

(defmethod render :ThematicBreak [pdf-config node] 
  (let [m (:line pdf-config)] 
    (case m :pagebreak [:pagebreak] [:line m])))

(defmethod render :LineBreak [pdf-config node]
  [:spacer (-> pdf-config :spacer :single-value)])

(defmethod render :HtmlBlock [pdf-config node]
  (->> node 
       .getLiteral
       (html-tag->clj-pdf pdf-config) 
       ;; This preserve line-break count equivalency (parag = 2 breaks)
       (merge [:paragraph (:paragraph pdf-config)])))

(defmethod render :HtmlInline [pdf-config node]
  (->> node .getLiteral (html-tag->clj-pdf pdf-config)))

(defmethod render :Literal [pdf-config node]
  (.getLiteral node))

(defmethod render :default [_ node]  
  (.getLiteral node))


;;;; PRE-PROCESSORS

(defn split-with-delim 
  "Splits a string into components based on a given delimiter 
   (which will be more then one character) 
   but at the same time keep the delimiters.
   Source: https://goo.gl/vkca2q"
  [s delim]
  (str/split s (re-pattern (str "(?=" delim ")|(?<=" delim ")"))))

(defn mark-extra-line-breaks*
  "Takes string and switches each third+ consecutive linebreaks
   to \"<-br->\" special markers"
  [s]
  (as-> s $
      (str/replace $ #"<br>|<br/>|<br />" "\n")
      (str/replace $ #"(?<=\n\n)\n" "<br/>")
      (split-with-delim $ "<br/>")
      (partition-by #(= "<br/>" %) $)
      (mapv #(if (some #{"<br/>"} %) 
               (str "<br n=\"" (count %) "\" />") 
               (first %)) 
            $)
      (apply str $)))
      
(defn has-extra-line-breaks-to-mark? [pdf-config s] 
  (and (re-find #"<br>|<br/>|<br />|\n" s)
       (-> pdf-config :spacer :allow-extra-line-breaks?)))

(defmulti mark-extra-line-breaks has-extra-line-breaks-to-mark?)

(defmethod mark-extra-line-breaks true [pdf-config s]
  (mark-extra-line-breaks* s))

(defmethod mark-extra-line-breaks :default [pdf-config s] s)


;;;; POST-PROCESSORS

(defn merge-maybe [a b]
  (if (and (map? a) (map? b)) (merge a b) b))


;;;; API

(defn markdown->clj-pdf
  "Takes a string of markdown and a renderer configuration and converts the string
  to a hiccup-compatible data structure."
  ([s]
   (markdown->clj-pdf {} s))
  ([pdf-config s]
   (let [config (merge-with merge-maybe default-pdf-config pdf-config)] 
     (->> s
          (mark-extra-line-breaks config)
          parse-markdown
          (render config)))))


;;;; USAGE

;;> (use 'clj-pdf-markdown.core :reload)
;;> (markdown->clj-pdf "This is a *test*.")
;;=> [:paragraph "This is a " [:chunk {:style :italic} "test"] "."]
