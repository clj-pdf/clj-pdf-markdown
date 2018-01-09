# clj-pdf-markdown

A small Clojure library for converting [CommonMark][1] markdown to [clj-pdf][2] syntax. 

At least for now, this library relies on top of [commonmark-java][3] which is a java-based markdown parser.

This is alpha software. Possible breaking changes can be expected. Feel free to contribute!

Note that clj-pdf-markdown is built for configurability, not performance.

[1]: http://spec.commonmark.org/
[2]: https://github.com/yogthos/clj-pdf
[3]: https://github.com/atlassian/commonmark-java

## Installation

Add the following dependency to `project.clj`:

[![clojars project](http://clojars.org/clj-pdf-markdown/latest-version.svg)](http://clojars.org/clj-pdf-markdown)


## Usage

### Basic usage
You can convert a markdown string to clj-pdf format using `markdown->clj-pdf`:

```clojure
user=> (require '[clj-pdf-markdown.core :refer [markdown->clj-pdf]])
nil
user=> (markdown->clj-pdf "This is a *test*.")
[:paragraph {} "This is a " [:phrase {:style :italic} "test"] "."]
```

### Configuration

You can pass a map of pdf custom args the converter to tweak the output. 

```clojure
user=> (markdown->clj-pdf {:paragraph {:align :center}} "This is a *test*.")
[:paragraph {:align :center} "This is a " [:phrase {:style :italic} "test"] "."]
```
Any custom map provided will the merged to following the default map used by the library:
```clojure
(def pdf-default-args 
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
```

### Documentation
- [anchor](#anchor)
- [heading](#heading)
- [image](#image)
- [line](#line)
- [list](#list)
- [paragraph](#paragraph)
- [spacer](#spacer)

#### anchor
```clojure
user=> (markdown->clj-pdf {} "[I'm a single link](https://www.google.com)")
[:anchor {:target "https://www.google.com"} "I'm a single link"]
```
Note that any title arg in markdown will be ignored, since it is not supported in clj-pdf:
```clojure
user=> (markdown->clj-pdf {} "[I'm a single link with title](https://www.google.com \"Google's Homepage\")")
[:anchor {:target "https://www.google.com"} "I'm a single link with title"]
```
When not alone, the anchor will be wrapped in paragraph.
```clojure
user=> (markdown->clj-pdf {} "Text followed by: [a link](https://www.google.com)")
[:paragraph {} 
 "Text followed by: " 
 [:anchor {:target "https://www.google.com"} 
 "a link"]] 
             
user=> (markdown->clj-pdf {} "[link 1](https://www.google.com)[link 2](https://www.yahoo.com)")
[:paragraph {} 
 [:anchor {:target "https://www.google.com"} "link 1"] 
 [:anchor {:target "https://www.yahoo.com"} "link 2"]]
```

#### heading
```clojure
user=> (markdown->clj-pdf {} "# Title _1_")
[:heading {:style {:size 16}} "Title " [:phrase {:style :italic} "1"]]

user=> (markdown->clj-pdf {} "## Sub-title _1_")
[:heading {:style {:size 5}} "Sub-title " [:phrase {:style :italic} "1"]]
```
To change pdf args, you must specify which level of heading it is, from `:h1` to `:h6`:
```clojure
user=> (markdown->clj-pdf {:heading {:h2 {:style {:size 20}}}} "## Title _Big_") 
[:heading {:style {:size 20}} "Title " [:phrase {:style :italic} "Big"]]
```

#### image
```clojure
user=> (markdown->clj-pdf {} "![alt text](https://github.com/adam-p/markdown-here/raw/master/src/common/images/icon48.png \"Logo Title Text 1\")")
[:image {:annotation ["Logo Title Text 1" "alt text"]} "https://github.com/adam-p/markdown-here/raw/master/src/common/images/icon48.png"]
```
Images can also be inserted inline with other text by wrapping it inside of a chunk element.
```clojure
user=> (markdown->clj-pdf {} "This is an image: ![alt text](http://via.placeholder.com/350x150 \"Logo Title Text 1\")")
[[:paragraph {} "Text before"] 
 [:line {}] 
 [:paragraph {} "text after"]]
```
Also, chunk will be added if x and y values are provided in image sub-map. These are relative offsets for the image. The image element itself still accepts it's normal properties shown above.
```clojure
user=> (markdown->clj-pdf {:image {:x 10 :y 10}} "![alt text](http://via.placeholder.com/350x150 \"Logo Title Text 1\")")
[:chunk {:x 10 :y 10} [:image {:annotation ["Logo Title Text 1" "alt text"]} "http://via.placeholder.com/350x150"]]

```


#### line
```clojure
user=> (markdown->clj-pdf {} "Text before
  #_=> ***
  #_=> text after")
[[:paragraph {} "Text before"] [:line {}] [:paragraph {} "text after"]]
```
When `:pagebreak` is provided a :line arg value, this will provide a `[:pagebreak]` instead of a line!
```clojure
(markdown->clj-pdf {:line :pagebreak} "Text before
  #_=> ***
  #_=> Text after")
[[:paragraph {} "Text before"] [:pagebreak] [:paragraph {} "Text after"]]
```

#### list

Markdown supports two king of lists, ordered lists (`:ol`) and unordered lists (`:ul`).

```clojure
user=> (markdown->clj-pdf {} "* List item one
  #_=> * List item two")
[:list {:symbol "• "} "List item one" "List item two"]

user=> (markdown->clj-pdf {} "1. This is the first item
  #_=> 2. This is the second item")
[:list {:numbered true} "This is the first item" "This is the second item"]
```

If you want to customize lists, you must specify the right intermediate key like so:

```clojure
user=> (markdown->clj-pdf {:list {:ul {:symbol "* "}}} "* List item one
  #_=> * List item two")
[:list {:symbol "* "} "List item one" "List item two"]

user=> (markdown->clj-pdf {:list {:ol {:roman true}}} "1. This is the first item
  #_=> 2. This is the second item")
[:list {:roman true} "This is the first item" "This is the second item"]
```

#### paragraph
By default, a string will be wraped in paragraph except if it is plain and alone.
```clojure
user=> (markdown->clj-pdf {} "This is simple text")
"This is simple text"
user=> (markdown->clj-pdf {} "Content with some *style*.")
[:paragraph {} "Content with some " [:phrase {:style :italic} "style"] "."]
```

Note that, you don't want to unwrap single strings, you can specify it:
```clojure
user=> (markdown->clj-pdf {:wrap {:unwrap-singleton? false} "This is simple text")
[[:paragraph {} "This is simple text"]]

```
When there is more than one element at the document level, it gets globally wrapped in a vector. Undercover, clj-pdf will expand sequences containing elements:
``` 
(clj-pdf.core/pdf
 [{}
  [[:paragraph "1"] [:paragraph "2"]]]
 "doc.pdf")
 ```
is equivalent to
```
(clj-pdf.core/pdf
 [{}
  [:paragraph "1"] 
  [:paragraph "2"]]
 "doc.pdf")
```

#### spacer

```clojure
user=> (markdown->clj-pdf {} "This is
  #_=> a spacer.")
[:paragraph {} "This is" [:spacer 0] "a spacer."]

user=> (markdown->clj-pdf {:spacer {:single-value 10}} "This is
  #_=> a huge spacer")
[:paragraph {} "This is" [:spacer 10] "a huge spacer"]
```
Note that if you break the line twice, you will get multiple paragraphs instead of spacers:

```clojure
user=> (markdown->clj-pdf {} "This is paragraph 1
  #_=> 
  #_=> paragraph 2
  #_=> 
  #_=> paragraph 3")
[[:paragraph {} "This is paragraph 1"] [:paragraph {} "paragraph 2"] [:paragraph {} "paragraph 3"]]
```
Also, by default, clj-pdf-markdown render extra line-breaks (third+)
```clojure
user=> (markdown->clj-pdf {:spacer {:extra-starting-value 0 :allow-extra-line-breaks? true}} 
        "Text\n\n\nText after 3 line-breaks\n\n\n\n\nText after 5 line-breaks")
[[:paragraph {} "Text"] 
 [:paragraph {} [:spacer 0] "Text after 3 line-breaks"] 
 [:paragraph {} [:spacer 2] "Text after 5 line-breaks"]]
user=> (markdown->clj-pdf {:spacer {:extra-starting-value 1 :allow-extra-line-breaks? true}} 
        "Text\n\n\nText after 3 line-breaks\n\n\n\n\nText after 5 line-breaks")
[[:paragraph {} "Text"] 
 [:paragraph {} [:spacer 1] "Text after 3 line-breaks"] 
 [:paragraph {} [:spacer 3] "Text after 5 line-breaks"]]
```
Note that extra line-breaks can be disabled. 
```clojure
user=> (markdown->clj-pdf {:spacer {:extra-starting-value 1 :allow-extra-line-breaks? false}}
        "Text\n\n\nText after 3 line-breaks\n\n\n\n\nText after 5 line-breaks")
[[:paragraph {} "Text"] 
 [:paragraph {} "Text after 3 line-breaks"] 
 [:paragraph {} "Text after 5 line-breaks"]]
```
## Changelog
0.2.0 (Jan 9, 2018)
* removed commonmark-hiccup dep
* controled wrapping behavior with :wrap option
* handles extra spacers


## License

Copyright © 2017 Leon Talbot

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
