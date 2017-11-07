(ns clj-pdf-markdown.core
  (:require
   [clojure.walk :as walk]
   [commonmark-hiccup.core :as markdown]))


(def pdf-default-args 
  {:anchor   {}
   :heading  {:h1 {:style {:size 16}}
              :h2 {:style {:size 15}}
              :h3 {:style {:size 14}}
              :h4 {:style {:size 13}}
              :h5 {:style {:size 12}}
              :h6 {:style {:size 11}}}
   :image     {:xscale    1
               :yscale    1
               :pad-left  10
               :pad-right 10}
   :line      {}
   :list      {:ol {:numbered true}
               :ul {:symbol   "• "}}
   :paragraph {}
   :spacer    0})

(defn markdown-config [pdf-args]
  {:renderer
   {:nodes 
    {org.commonmark.node.Document          :content
     org.commonmark.node.Heading           (if (:heading pdf-args) 
                                             [:heading 
                                              :node-level
                                              :content]
                                             :content)
     org.commonmark.node.Paragraph         (if-let [p (:paragraph pdf-args)] 
                                             [:paragraph p :content]
                                             :content)
     org.commonmark.node.Text              :node-literal
     org.commonmark.node.BulletList        [:list (get-in pdf-args [:list :ul]) 
                                            :content]
     org.commonmark.node.OrderedList       [:list (get-in pdf-args [:list :ol]) 
                                            :content]
     org.commonmark.node.ListItem          :content
     org.commonmark.node.BlockQuote        :node-literal
     org.commonmark.node.HtmlBlock         :node-literal
     org.commonmark.node.HtmlInline        :node-literal
     org.commonmark.node.FencedCodeBlock   :node-literal
     org.commonmark.node.IndentedCodeBlock :node-literal
     org.commonmark.node.Code              :node-literal
     org.commonmark.node.Link              [:anchor 
                                            (merge (:anchor pdf-args)
                                                   {:target :node-destination}) 
                                            :content]
     org.commonmark.node.Image             [:image
                                            (merge (:image pdf-args) 
                                                   {:annotation [:node-title 
                                                                 :text-content]})
                                            :node-destination]
     org.commonmark.node.Emphasis          [:phrase {:style :italic} :content]
     org.commonmark.node.StrongEmphasis    [:phrase {:style :bold} :content]
     org.commonmark.node.ThematicBreak     (let [m (:line pdf-args)]
                                             (case m
                                               :pagebreak [:pagebreak]
                                               [:line m]))
     org.commonmark.node.SoftLineBreak     [:spacer (:spacer pdf-args)]
     org.commonmark.node.HardLineBreak     [:spacer (:spacer pdf-args)]}}})


(defn merge-maybe [a b]
  (if (and (map? a) (map? b))
    (merge a b)
    b))

(defn replace-nodes 
  "replaces specific nodes like :heading with special config"
  [pdf-args nodes]
  (walk/postwalk 
   (fn [node]
     (if (and (vector? node) (keyword? (first node)))
       (let [[tag arg & [remaining]] node
             rem (vec remaining)]
         (case tag

           ;; In heading, converts :node-level num to clj-pdf arg
           :heading
           (into 
            [:heading 
             (get-in pdf-args 
                     [:heading (keyword (str "h" arg))])]
            rem)
           
           ;; Image cannot be nested in paragraph
           ;; https://github.com/yogthos/clj-pdf/issues/107
           
           :paragraph 
           (if (some #(when (sequential? %) 
                       (= (first %) :image)) 
                     remaining)
             remaining
             node)
           node))
       node))
   nodes))

(defn expand-seqs 
  "https://github.com/weavejester/hiccup/wiki/Syntax#expanding-seqs"
  [s]
  (cond
    (seq? s) (mapcat expand-seqs s)
    (vector? s) [(into [] (mapcat expand-seqs s))]
    :else [s]))

(defn unwrap-singleton [nodes]
  (cond 
    (string? nodes) nodes
    (and (coll? nodes) (= (count nodes) 1)) (first nodes)
    :else (vec nodes)))

;; API

(defn markdown->clj-pdf 
  "Takes a string, and get back clj-pdf view data-structure"
  ([s] (markdown->clj-pdf {} s)) 
  ([args s]
   (let [pdf-args (merge-with merge-maybe pdf-default-args args)
         config   (markdown-config pdf-args)] 
     (->> s 
          (markdown/markdown->hiccup config) 
          (replace-nodes pdf-args) 
          expand-seqs
          unwrap-singleton))))


;; USAGE
;;
;;> (use 'clj-pdf-markdown.core :reload)
;;> (markdown->clj-pdf "This is a *test*.")
;;=> [:paragraph "This is a " [:chunk {:style :italic} "test"] "."]
