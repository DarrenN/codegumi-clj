(ns codegumi.views
  (:require [cheshire.core :refer :all])
  (:use [hiccup core page]))

(defn page-template [photos]
  (html5
   [:head
    [:title "CodeGumi"]
    (include-css "/css/style.css")]
   [:body
    [:h1 "Hello " [:span {:class "title-tag"}]]
    [:form {:id "tag-form"}
     [:input {:type "text" :placeholder "Enter search tag" :id "tag-input"}]
     [:input {:type "submit" :value "Search" :id "tag-submit"}]]
    [:ul {:id "photos"}]
    [:script (str "var Flicky = " photos ";")]
    (include-js "/js/script.js")]))

(defn index-page []
  (html5
   [:head
    [:title "CodeGumi"]
    (include-css "/css/style.css")]
   [:body
    [:h1 "Hello " [:span {:class "title-tag"}]]
    [:form {:id "tag-form"}
     [:input {:type "text" :placeholder "Enter search tag" :id "tag-input"}]
     [:input {:type "submit" :value "Search" :id "tag-submit"}]
     [:button {:id "btn-pause"} "Pause"]
     [:button {:id "btn-play"} "Play"]]
    [:ul {:id "photos"}]
    (include-js "/js/script.js")]))
