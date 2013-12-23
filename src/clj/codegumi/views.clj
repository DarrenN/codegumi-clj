(ns codegumi.views
  (:require [cheshire.core :refer :all])
  (:use [hiccup core page]))

(defn page-template
  ([]
     (page-template nil))
  ([photos]
     (html5
      [:head
       [:title "CodeGumi | 符組"]
       (include-css "/css/style.css")]
      [:body
       [:form {:id "tag-form"}
        [:a {:href "http://darrenknewton.com" :title "Back to blog"} [:img {:src "/img/logo_solo.svg" :width 40 :height 40 :class "logo" :alt "v25media"}]]
        [:input {:type "text" :placeholder "Enter search tag" :id "tag-input" :class "tag-input"}]
        [:input {:type "submit" :value "Search" :id "tag-submit" :class "tag-form-button"}]
        [:button {:id "btn-pause" :class "tag-form-button"} "Pause"]
        [:button {:id "btn-play" :class "tag-form-button"} "Play"]
        [:h1 [:span {:class "title-tag"}]]]
       [:ul {:id "photos"}]
       (when-not (nil? photos)
         [:script (str "var Flicky = " photos ";")])
       (include-js "/js/script.js")])))
