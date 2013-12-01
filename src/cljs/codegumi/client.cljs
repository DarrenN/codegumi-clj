(ns codegumi.core
  (:require
    [clojure.string :as string]
    [dommy.utils :as utils]
    [dommy.core :as dommy]
    [enfocus.core :as ef]
    [enfocus.events :as events]
    [enfocus.effects :as effects]
    [ajax.core :refer [GET POST]]
    [clojure.browser.repl :as repl])
  (:require-macros
   [enfocus.macros :as em])
  (:use-macros
   [dommy.macros :only [node sel sel1]]))

(repl/connect "http://localhost:9000/repl")

(defn ^:export log [thing] (.log js/console (clj->js thing)))

(aset js/window "log" log)

(def photo-sizes ["l" "m" "n" "o" "q" "s" "t" "z"])
(def window-dimensions {:width (.-innerWidth js/window) :height (.-innerHeight js/window)})

; Atoms to hold our photos and listeners
(def plist (atom []))
(def destroy-listeners (atom []))
(def add-listeners (atom []))
(def item-counter (atom 0))

(defn register-listener [list f]
  "Put a listener function on an atom"
  (swap! list conj f))

(defn add-item [i]
  "Add an item to the atom and call listeners on it"
  (doseq [f @add-listeners] (f i))
  (swap! plist conj i))

(defn remove-item []
  "Remove item from atom. We use reset! to make this work"
  (doseq [f @destroy-listeners] (f (first @plist)))
  (reset! plist (vec (rest @plist))))

(defn create-buffer [size]
  "Return a function that keeps plist at size when adding"
  (fn [item]
    (add-item item)
    (when (> (count @plist) size)
      (remove-item))))

(defn get-photo-id [item]
  (string/join "_" ["#photo" (item :counter)]))

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

(defn get-photo-position [image]
  "Generate a style string with top, left and z-index"
  (let [offset-y (/ (image :height) 2)
        offset-x (/ (image :width) 2)
        top (- (rand (window-dimensions :height)) offset-y)
        left (- (rand (window-dimensions :width)) offset-x)
        zindex (get-zindex image)]
    (str "top: " top "px; left: " left "px; z-index: " zindex)))

(em/defaction fade-out-photo [id]
  [id]
  (effects/chain (effects/fade-out (+ 1000 (rand 500)))
                 (ef/remove-node)))

(em/defaction fade-in-photo [id]
  [id]
  (effects/chain (effects/fade-in (+ 1000 (rand 500)))))

(defn image-template [item image position]
  (node
     [:li {:id (string/join "_" ["photo" (item :counter)]) :class (image :size) :style position}]))

(defn insert-photo-node [item]
  ; We have to create the Image dynamically so we can attach an onload
  ; handler which allows it to fade-in AFTER it has loaded from the server
  (let [image (get-photo-size item)
        position (get-photo-position image)
        li (image-template item image position)
        img (js/Image.)]
    ; set img properties
    (set! (.-src img) (image :url))
    (set! (.-width img) (image :width))
    (set! (.-height img) (image :height))
    (set! (.-alt img) (get item "owner"))
    (set! (.-title img) (get item "title"))
    (set! (.-onload img) (fn [] (fade-in-photo (get-photo-id item))))
    (dommy/append! li img)
    (dommy/append! (sel1 :#photos) li)
    item))

(defn on-item-add [item]
  (when-not (nil? item)
    (insert-photo-node item)))

(defn on-item-remove [item]
  (when-not (nil? item)
    (fade-out-photo (get-photo-id item))))

(register-listener destroy-listeners on-item-remove)
(register-listener add-listeners on-item-add)

; Create the main sliding buffer
(def photo-queue (create-buffer 25))

; Recur through photos with a 50ms delay, the item-counter is a global
; and used for ids
(defn append-photos [response]
  (let [r (first response)
        photo (conj r {:counter @item-counter})]
    (when-not (nil? r)
      (photo-queue photo)
      (swap! item-counter inc)) ; increment global counter
    (when-not (empty? response)
      (js/setTimeout #(append-photos (vec (rest response))) 50))))

(defn append-tags [response]
  (dommy/set-text! (sel1 ".title-tag") (string/join "," (get response "tags"))))

(defn handler [response]
  (aset js/window "response" response)
  (append-photos (get response "photos"))
  (append-tags response))

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text)))

(defn fetch-tag [tag]
  (GET (str "/tags/" tag) {:handler handler
                           :error-handler error-handler
                           :headers {:Accept "application/json"}}))

(defn submit-handler [e]
  (.preventDefault e)
  (fetch-tag (dommy/value (sel1 :#tag-input))))

(dommy/listen! (sel1 :#tag-submit) :click submit-handler)

(if (nil? (.-Flicky js/window))
  (GET "/tags" {:handler handler
                :error-handler error-handler})
  (handler (js->clj (.-Flicky js/window))))
