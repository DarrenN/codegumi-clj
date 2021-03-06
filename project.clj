(defproject codegumi "0.2.0"
  :description "codegumi.com in Clojure/ClojureScript"
  :url "http://codegumi.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2080"]
                 [org.clojure/tools.reader "0.7.10"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/core.async "0.1.242.0-44b1e3-alpha"]
                 [compojure "1.1.6"]
                 [clj-http "0.7.7"]
                 [cheshire "5.2.0"]
                 [hiccup "1.0.4"]
                 [domina "1.0.2"]
                 [enfocus "2.0.2"]
                 [cljs-ajax "0.2.2"]
                 [environ "0.4.0"]
                 [hiccups "0.2.0"]
                 [me.raynes/fs "1.4.4"]
                 [clj-time "0.6.0"]
                 [com.taoensso/timbre "2.7.1"]
                 [ring "1.3.2"]]
  :plugins [[lein-cljsbuild "1.0.1-SNAPSHOT"]
            [lein-ring "0.8.8"]
            [lein-environ "0.4.0"]]
  :source-paths ["src/clj" "src/cljs"]
  :hooks [leiningen.cljsbuild]
  :main codegumi.handler
  :ring {:handler codegumi.handler/app}
  :profiles
  {:uberjar {:aot :all
             :cljsbuild {:builds [{:source-paths ["src/cljs"]
                                   :compiler {:output-to "resources/public/js/script.js"
                                              :optimizations :simple
                                              :pretty-print false}
                                   }]}}
   :dev {:dependencies [[ring-mock "0.1.5"]]
         :cljsbuild {:builds [{:source-paths ["src/cljs"]
                               :compiler {:output-dir "resources/public/js/cljs-out"
                                          :output-to "resources/public/js/script.js"
                                          :optimizations :simple
                                          :pretty-print true
                                          :source-map "resources/public/js/script.js.map"}}]}}})
