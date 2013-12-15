(ns codegumi.photo
  (:require
   [clojure.string :as string]
   [goog.object :as gobj]
   [domina :as dom]
   [domina.css :as css]
   [enfocus.core :as ef]
   [hiccups.runtime :as hiccupsrt])
  (:require-macros
   [hiccups.core :as hiccups]))

;; Enfocus spits out hiccup temapltes to console.log in ef/html
;; unless we set this
(set! (.-debug enfocus.core) false)

; Functions to generate size, zindex and position and DOM element of a Photo

(def photo-sizes ["l" "m" "n" "o" "q" "s" "t" "z"])

(defn get-photo-id [item]
  (str "#photo_" (item :counter)))

(defn get-photo-size [photo]
  "Randomly find a photo size from the object, recur until a valid one is found"
  (let [size (nth photo-sizes (rand (count photo-sizes)))]
    (if (contains? photo (str "url_" size))
      {:size size
       :url (get photo (str "url_" size))
       :width (get photo (str "width_" size))
       :height (get photo (str "height_" size))}
      (get-photo-size photo))))

(defn get-zindex [image]
  "We need large images at a lower zIndex"
  (cond
    (> (image :width) 900) (Math/round (+ 500 (rand 25)))
    (> (image :width) 500) (Math/round (+ 525 (rand 25)))
    (> (image :width) 200) (Math/round (+ 550 (rand 25)))
    :else (Math/round (+ 575 (rand 25)))))

(defn get-photo-position [image, window]
  "Generate a style string with top, left and z-index"
  (let [offset-y (/ (image :height) 2)
        offset-x (/ (image :width) 2)
        top (- (rand (window :height)) offset-y)
        left (- (rand (window :width)) offset-x)
        zindex (get-zindex image)]
    (str "top: " (.floor js/Math top) "px; left: " (.floor js/Math  left) "px; z-index: " zindex)))

(defn image-template [item image position]
  (hiccups/html
   [:li {:id (str "photo_" (item :counter)), :class (str (image :size) " photo-li"), :style position}
    [:div {:class "image-mask"}]]))

(defn build-photo-node [item window onload]
  ; We have to create the Image dynamically so we can attach an onload
  ; handler which allows it to fade-in AFTER it has loaded from the server
  (let [image (get-photo-size item)
        position (get-photo-position image window)
        li (image-template item image position)
        img (js/Image.)
        a (hiccups/html [:a {:class "image-link", :href (image :url)}])]
    ; set img properties
    (set! (.-src img) (image :url))
    (set! (.-width img) (image :width))
    (set! (.-height img) (image :height))
    (set! (.-alt img) (get item "owner"))
    (set! (.-title img) (get item "title"))
    (set! (.-className img) "photo")
    (set! (.-onload img) onload)
    (let [aimg (dom/single-node (dom/append! (dom/html-to-dom a) img))]
      (dom/single-node (dom/append! (dom/html-to-dom li) aimg)))
    ))

(defn get-photo-center [li]
  "Return a tuple of the x y center point of the img"
  (let [img (dom/nodes (css/sel li "img"))
        xy [(/ (dom/attr img :width) 2) (/ (dom/attr img :height) 2)]]
    (map Math/floor xy)))

(defn get-highest-zindex [class]
  "Return the highest zIndex in a stack of photos"
  (let [li (map (fn [l] (.parseInt js/window (dom/style l :z-index))) (dom/nodes (dom/by-class class)))]
    (apply max 0 li)))
