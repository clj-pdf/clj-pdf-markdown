# clj-pdf-markdown

A small Clojure library for converting [CommonMark][1] markdown to [clj-pdf][2] syntax. 

This library relies on top of [commonmark-hiccup][3].

This is alpha software. Possible breaking changes can be expected. Feel free to contribute!

[1]: http://spec.commonmark.org/
[2]: https://github.com/yogthos/clj-pdf


## Installation

Add the following dependency to `project.clj`:

[![clojars project](http://clojars.org/clj-pdf-markdown/latest-version.svg)](http://clojars.org/clj-pdf-markdown)


## Under the hood

clj-pdf-markdown is built on top of [commonmark-hiccup][3] parser (itself built on top of [commonmark-java][4]) which transforms the CommonMark AST to Hiccup-compatible Clojure data structures. 

clj-pdf-markdown modifies commonmark-hiccup condifguration to render clj-pdf syntax instead of Hiccup or HTML.

Like commonmark-hiccup, clj-pdf-markdown is built for configurability, not performance.

[3]: https://github.com/bitterblue/commonmark-hiccup
[4]: https://github.com/atlassian/commonmark-java

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
   :spacer    0})
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
user=> (markdown->clj-pdf {} "[I'm an inline-style link](https://www.google.com)")
[:paragraph {} [:anchor {:target "https://www.google.com"} "I'm an inline-style link"]]
```
Note that any title arg in markdown will be ignored, since it is not supported in clj-pdf:
```clojure
user=> (markdown->clj-pdf {} "[I'm an inline-style link with title](https://www.google.com \"Google's Homepage\")")
[:paragraph {} [:anchor {:target "https://www.google.com"} "I'm an inline-style link with title"]]
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
By default, the library will wrap strings in paragraph except if it contains images or lists. 
```clojure
user=> (markdown->clj-pdf {} "This is simple text")
[:paragraph {} "This is simple text"]
```

A boolean can also be passed as a value of clj-pdf syntax element like `:paragraph`. This will renders the content inside the element. :

```clojure
user=> (markdown->clj-pdf {:paragraph false} "Naked content.")
"Naked content."
```

Note that, even if in the example we got rid of `:paragraph` tag, a vector will wrap content if more than one element is returned:

```clojure
user=> (markdown->clj-pdf {:paragraph false} "Content with some *style*.")
["Content with some " [:phrase {:style :italic} "style"] "."]
```

Undercover, clj-pdf will expand sequences containing elements:
``` 
(clj-pdf.core/pdf
 [{}
   ["This is a " [:phrase {:style :italic} "test"] "."]
 "doc.pdf")
 ```
is equivalent to
```
(clj-pdf.core/pdf
 [{}
   "This is a "
   [:phrase {:style :italic} "test"] 
   "."
 "doc.pdf")
```

Àlso, when a markdown string contains an image, paragraph wrapping gets disabled to allow clj-pdf to render the image:
```clojure
user=> (markdown->clj-pdf {} "![alt text](https://github.com/adam-p/markdown-here/raw/master/src/common/images/icon48.png \"Logo Title Text 1\")")
[:image {:annotation ["Logo Title Text 1" "alt text"]} "https://github.com/adam-p/markdown-here/raw/master/src/common/images/icon48.png"]

user=> (markdown->clj-pdf {} "This is an image: ![alt text](https://github.com/adam-p/markdown-here/raw/master/src/common/images/icon48.png \"Logo Title Text 1\")")
["This is an image: " [:image {:annotation ["Logo Title Text 1" "alt text"]} "https://github.com/adam-p/markdown-here/raw/master/src/common/images/icon48.png"]]
```
See: [Image not showing this bellow paragraph #107](https://github.com/yogthos/clj-pdf/issues/107)

#### spacer

```clojure
user=> (markdown->clj-pdf {} "This is
  #_=> a spacer.")
[:paragraph {} "This is" [:spacer 0] "a spacer."]

user=> (markdown->clj-pdf {:spacer 10} "This is
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


## License

Copyright © 2017 Leon Talbot

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
