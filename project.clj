(defproject codegumi "0.1.0-SNAPSHOT"
  :description "codegumi.com in Clojure/ClojureScript"
  :url "http://codegumi.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2030"]
                 [org.clojure/tools.reader "0.7.10"]
                 [compojure "1.1.6"]
                 [clj-http "0.7.7"]
                 [cheshire "5.2.0"]
                 [hiccup "1.0.4"]
                 [prismatic/dommy "0.1.1"]
                 [cljs-ajax "0.2.2"]
                 [environ "0.4.0"]]
  :plugins [[lein-cljsbuild "1.0.0-alpha2"]
            [lein-ring "0.8.8"]
            [lein-environ "0.4.0"]]
  :source-paths ["src/clj" "src/cljs"]
  :cljsbuild {
              :builds [{
                        ; Path to top-level CLJS dir
                        :source-paths ["src/cljs"]
                        ; Compiler options:
                        :compiler {
                                   :output-dir "target/cljs-out"
                                   :output-to "resources/public/js/script.js"
                                   :optimizations :simple
                                   :pretty-print true}
                        }]}
  :ring {:handler codegumi.handler/app}
  :profiles {
             :dev {:dependencies [[ring-mock "0.1.5"]]}})
