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
     [:input {:type "submit" :value "Search" :id "tag-submit" :class "tag-form-button"}]
     [:button {:id "btn-pause" :class "tag-form-button"} "Pause"]
     [:button {:id "btn-play" :class "tag-form-button"} "Play"]]
    [:ul {:id "photos"}]
    (include-js "/js/script.js")]))
