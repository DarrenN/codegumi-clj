(ns codegumi.views
  (:require
   [cheshire.core :refer :all]
   [hiccup.core :refer :all]
   [hiccup.page :refer :all]))

(defn page-template
  ([]
     (page-template nil))
  ([photos]
     (html5
      [:head
       [:title "CodeGumi | 符組"]
       (include-css "/css/font-awesome.min.css")
       (include-css "/css/style.css")]
      [:body
       [:script (str "(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
  })(window,document,'script','//www.google-analytics.com/analytics.js','ga');
  ga('create', 'UA-33703989-1', 'codegumi.com');
  ga('send', 'pageview');")]
       [:form {:id "tag-form"}
        [:a {:href "http://darrenknewton.com" :title "Back to blog"} [:img {:src "/img/logo_solo.svg" :width 40 :height 40 :class "logo" :alt "v25media"}]]
        [:input {:type "text" :placeholder "Enter search tag" :id "tag-input" :class "tag-input"}]
        [:button {:id "tag-submit" :class "tag-form-button"} [:i {:class "fa fa-search"}]]
        [:button {:id "btn-pause" :class "tag-form-button"} [:i {:class "fa fa-pause"}]]
        [:button {:id "btn-play" :class "tag-form-button"} [:i {:class "fa fa-play"}]]
        [:h1 [:span {:class "title-tag"}]]]
       [:ul {:id "photos"}]
       (when-not (nil? photos)
         [:script (str "var Flicky = " photos ";")])
       (include-js "/js/script.js")])))
