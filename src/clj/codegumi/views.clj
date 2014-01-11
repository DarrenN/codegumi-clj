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
       [:meta {:http-equiv "Content-Type" :content "text/html; charset=UTF-8"}]
       [:meta {:charset "utf-8"}]
       [:meta {:content "IE=edge,chrome=1" :http-equiv "X-UA-Compatible"}]
       [:meta {:content "width=device-width" :name "viewport"}]
       [:meta {:content "yes" :name "apple-mobile-web-app-capable"}]
       [:meta {:content "Darren Newton" :name "author"}]
       [:meta {:content "A little experiment with the Flickr photo APIs - written in Clojure/ClojureScript" :name "description"}]
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
        [:a {:href "/" :title "CodeGumi"} [:img {:src "/img/logo_solo.svg" :width 40 :height 40 :class "logo" :alt "v25media"}]]
        [:input {:type "text" :placeholder "Enter search tag" :id "tag-input" :class "tag-input"}]
        [:button {:id "tag-submit" :class "tag-form-button" :title "Search"} [:i {:class "fa fa-search"}]]
        [:button {:id "btn-pause" :class "tag-form-button" :title "Pause"} [:i {:class "fa fa-pause"}]]
        [:button {:id "btn-play" :class "tag-form-button" :title "Play"} [:i {:class "fa fa-play"}]]
        [:h1
         [:a {:href "http://darrennewton.com" :title "Darren Newton"} "Me"]
         " | "
         [:a {:href "https://github.com/DarrenN/codegumi-clj" :title "source code"} "GitHub"]
         [:span {:class "title-tag" :title "Current search tag"}]]]
       [:ul {:id "photos"}]
       (when-not (nil? photos)
         [:script (str "var Flicky = " photos ";")])
       (include-js "/js/script.js")])))
