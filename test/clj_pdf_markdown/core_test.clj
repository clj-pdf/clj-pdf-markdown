(ns clj-pdf-markdown.core-test
  (:require [clojure.test :refer :all]
            [clj-pdf-markdown.core :refer :all]))

(deftest markdown->clj-pdf-test
 
  (testing "anchor"
    (is (= [:anchor {:target "https://www.google.com"} 
             "I'm a single link"]
           (markdown->clj-pdf {} 
            "[I'm a single link](https://www.google.com)")))
    (is (= [:anchor 
             {:target "https://www.google.com"} 
             "I'm a single link with title"]
           (markdown->clj-pdf {} 
             "[I'm a single link with title](https://www.google.com \"Google's Homepage\")")))
    (is (= [:paragraph {} 
            "Text followed by: " 
            [:anchor {:target "https://www.google.com"} 
             "a link"]]
           (markdown->clj-pdf {} 
             "Text followed by: [a link](https://www.google.com)")))
    (is (= [:paragraph {} 
            [:anchor {:target "https://www.google.com"} "link 1"] 
            [:anchor {:target "https://www.yahoo.com"} "link 2"]]
           (markdown->clj-pdf {}
            "[link 1](https://www.google.com)[link 2](https://www.yahoo.com)"))))

  (testing "heading"
    (is (= [:heading {:style {:size 16}} 
            "Title " [:phrase {:style :italic} "1"]]
           (markdown->clj-pdf {} "# Title _1_")))
    (is (= [:heading {:style {:size 15}} 
            "Sub-title " [:phrase {:style :italic} "1"]]
           (markdown->clj-pdf {} "## Sub-title _1_")))
    (is (= [:heading {:style {:size 20}} 
            "Title " [:phrase {:style :italic} "Big"]]
           (markdown->clj-pdf 
            {:heading {:h2 {:style {:size 20}}}} 
            "## Title _Big_"))))
  
  (testing "image"
    (is (= [:image {:annotation ["Logo Title Text 1" "alt text"]} "http://via.placeholder.com/350x150"]
           (markdown->clj-pdf {} 
            "![alt text](http://via.placeholder.com/350x150 \"Logo Title Text 1\")")))
    (is (= [:chunk {:x 10 :y 10} [:image {:annotation ["Logo Title Text 1" "alt text"]} "http://via.placeholder.com/350x150"]]
           (markdown->clj-pdf {:image {:x 10 :y 10}} 
            "![alt text](http://via.placeholder.com/350x150 \"Logo Title Text 1\")")))
    (is (= [:paragraph {} "This is an image: " [:chunk {} [:image {:annotation ["Logo Title Text 1" "alt text"]} "http://via.placeholder.com/350x150"]]]
           (markdown->clj-pdf {}
            "This is an image: ![alt text](http://via.placeholder.com/350x150 \"Logo Title Text 1\")"))))
  
  (testing "line"
    (is (= [[:paragraph {} "Text before"] 
            [:line {}] 
            [:paragraph {} "text after"]]
           (markdown->clj-pdf {} "Text before\n***\ntext after")))

    (is (= [[:paragraph {} "Text before"] 
            [:pagebreak] 
            [:paragraph {} "Text after"]]
           (markdown->clj-pdf {:line :pagebreak} 
            "Text before\n***\nText after"))))

  (testing "list"
    (is (= [:list {:symbol "• "} "List item one" "List item two"]
           (markdown->clj-pdf {} "* List item one\n* List item two")))

    (is (= [:list {:numbered true} 
            "This is the first item" "This is the second item"]
           (markdown->clj-pdf {} 
            "1. This is the first item\n2. This is the second item")))

    (is (= [:list {:symbol "* "} "List item one" "List item two"]
           (markdown->clj-pdf 
            {:list {:ul {:symbol "* "}}} 
            "* List item one\n* List item two")))

    (is (= [:list {:symbol "• "} 
            [:paragraph {} "List " [:phrase {:style :bold} "styled item"]] 
            "List regular item"]
           (markdown->clj-pdf {} 
            "* List **styled item**\n* List regular item"))))

  (testing "nested lists"
    (is (->> (markdown->clj-pdf {} "* List item one\n  * List item two (nested)")
             (tree-seq sequential? #(drop 2 %))
             (some #{"List item two (nested)"}))))

  (testing "paragraph"
    (is (= "This is simple text"
           (markdown->clj-pdf {} "This is simple text")))
    
    (is (= [:paragraph {} "Content with some " [:phrase {:style :italic} "style"] "."]
           (markdown->clj-pdf {} "Content with some *style*.")))

    ;; IMPROVE THIS (UNWRAP GLOBALLY BUT KEEP ONE PARAGRAPH)
    (is (= [[:paragraph {} "This is simple text"]]
           (markdown->clj-pdf {:wrap {:unwrap-singleton? false}} "This is simple text"))))

  (testing "spacer"
    (is (= [:paragraph {} "This is" [:spacer 0] "a spacer."]
           (markdown->clj-pdf {} "This is\na spacer.")))
    
    (is (= [:paragraph {} "This is" [:spacer 10] "a huge spacer"]
           (markdown->clj-pdf {:spacer {:single-value 10}} "This is\na huge spacer")))
    
    (is (= [[:paragraph {} "This is paragraph 1"]
            [:paragraph {} "paragraph 2"]
            [:paragraph {} "paragraph 3"]]
           (markdown->clj-pdf
            {}
            "This is paragraph 1\n\nparagraph 2\n\nparagraph 3")))
    (is (= [[:paragraph {} "Text"] 
            [:paragraph {} [:spacer 0] 
             "Text after 3 line-breaks"] 
            [:paragraph {} [:spacer 2] 
             "Text after 5 line-breaks"]]
           (markdown->clj-pdf 
            {:spacer {:extra-starting-value 0 :allow-extra-line-breaks? true}} 
            "Text\n\n\nText after 3 line-breaks\n\n\n\n\nText after 5 line-breaks")))
    (is (= [[:paragraph {} "Text"] 
            [:paragraph {} [:spacer 0] 
             "Text after 3 line-breaks and before 5 other line-breaks"] 
            [:paragraph {} [:spacer 2]]]
           (markdown->clj-pdf 
            {:spacer {:extra-starting-value 0 :allow-extra-line-breaks? true}} 
            "Text\n\n\nText after 3 line-breaks and before 5 other line-breaks\n\n\n\n\n")))))

