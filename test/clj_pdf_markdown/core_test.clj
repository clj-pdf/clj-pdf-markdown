(ns clj-pdf-markdown.core-test
  (:require [clojure.test :refer :all]
            [clj-pdf-markdown.core :refer :all]))

(deftest markdown->clj-pdf-test
 
  (testing "anchor"
    (is (= (markdown->clj-pdf {} "[I'm an inline-style link](https://www.google.com)")
           [:paragraph {} 
            [:anchor {:target "https://www.google.com"} 
             "I'm an inline-style link"]]))
    (is (= (markdown->clj-pdf {} "[I'm an inline-style link with title](https://www.google.com \"Google's Homepage\")")
           [:paragraph {} [:anchor {:target "https://www.google.com"} "I'm an inline-style link with title"]])))

  (testing "heading"
    (is (= (markdown->clj-pdf {} "# Title _1_")
           [:heading {:style {:size 16}} 
            "Title " [:phrase {:style :italic} "1"]]))
    (is (= (markdown->clj-pdf {} "## Sub-title _1_")
           [:heading {:style {:size 15}} 
            "Sub-title " [:phrase {:style :italic} "1"]]))
    (is (= (markdown->clj-pdf 
            {:heading {:h2 {:style {:size 20}}}} 
            "## Title _Big_")
           [:heading {:style {:size 20}} 
            "Title " [:phrase {:style :italic} "Big"]])))
  
  (testing "image"
    (is (= (markdown->clj-pdf {} "![alt text](https://github.com/adam-p/markdown-here/raw/master/src/common/images/icon48.png \"Logo Title Text 1\")")
       [:image {:annotation ["Logo Title Text 1" "alt text"]} "https://github.com/adam-p/markdown-here/raw/master/src/common/images/icon48.png"])))
  
  (testing "line"
    (is (= (markdown->clj-pdf {} "Text before\n***\ntext after")
       [[:paragraph {} "Text before"] [:line {}] [:paragraph {} "text after"]]))

    (is (= (markdown->clj-pdf {:line :pagebreak} "Text before\n***\nText after")
           [[:paragraph {} "Text before"] [:pagebreak] [:paragraph {} "Text after"]])))

  (testing "list"
    (is (= (markdown->clj-pdf {} "* List item one\n* List item two")
       [:list {:symbol "• "} "List item one" "List item two"]))

(is (= (markdown->clj-pdf 
        {} 
         "1. This is the first item\n2. This is the second item")
       [:list {:numbered true} 
         "This is the first item" "This is the second item"]))

(is (= (markdown->clj-pdf 
        {:list {:ul {:symbol "* "}}} 
         "* List item one\n* List item two")
       [:list {:symbol "* "} "List item one" "List item two"]))

(is (= (markdown->clj-pdf 
        {:list {:ol {:roman true}}} 
        "1. This is the first item\n2. This is the second item")
       [:list {:roman true} "This is the first item" "This is the second item"])))
  
  (testing "paragraph"
    (is (= (markdown->clj-pdf {} "This is simple text")
           [:paragraph {} "This is simple text"]))
    
    (is (= (markdown->clj-pdf {:paragraph false} "Naked content.")
           "Naked content."))
    
    (is (= (markdown->clj-pdf {:paragraph false} "Content with some *style*.")
           ["Content with some " [:phrase {:style :italic} "style"] "."]))
    
    (is (= (markdown->clj-pdf {} "![alt text](https://github.com/adam-p/markdown-here/raw/master/src/common/images/icon48.png \"Logo Title Text 1\")")
           [:image {:annotation ["Logo Title Text 1" "alt text"]} 
            "https://github.com/adam-p/markdown-here/raw/master/src/common/images/icon48.png"]))
    
    (is (= (markdown->clj-pdf 
            {} 
            "This is an image: ![alt text](https://github.com/adam-p/markdown-here/raw/master/src/common/images/icon48.png \"Logo Title Text 1\")")
           ["This is an image: " 
            [:image {:annotation ["Logo Title Text 1" "alt text"]} 
             "https://github.com/adam-p/markdown-here/raw/master/src/common/images/icon48.png"]])))
  
  (testing "spacer"
    (is (= (markdown->clj-pdf {} "This is\na spacer.")
           [:paragraph {} "This is" [:spacer 0] "a spacer."]))
    
    (is (= (markdown->clj-pdf {:spacer 10} "This is\na huge spacer")
           [:paragraph {} "This is" [:spacer 10] "a huge spacer"]))
    
    (is (= (markdown->clj-pdf 
            {} 
            "This is paragraph 1\n\nparagraph 2\n\nparagraph 3")
           [[:paragraph {} "This is paragraph 1"] 
            [:paragraph {} "paragraph 2"] 
            [:paragraph {} "paragraph 3"]]))))

