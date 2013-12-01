(ns codegumi.core
  (:require
    [clojure.string :as string]
    [dommy.utils :as utils]
    [dommy.core :as dommy]
    [enfocus.core :as ef]
    [enfocus.events :as events]
    [enfocus.effects :as effects]
    [ajax.core :refer [GET POST]])
  (:require-macros
   [enfocus.macros :as em])
  (:use-macros
   [dommy.macros :only [node sel sel1]]))

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

(defn image-template [photo]
  (let [image (get-photo-size photo)
        position (get-photo-position image)]
    (node
     [:li {:id (string/join "_" ["photo" (photo :counter)]) :class (image :size) :style position}
      [:img {:src (image :url)
             :width (image :width)
             :height (image :height)
             :alt (get photo "owner")
             :title (get photo "title")}]])))

(defn insert-photo-node [item]
  (dommy/append! (sel1 :#photos) (image-template item))
  item)

(defn get-photo-id [item]
  (string/join "_" ["#photo" (item :counter)]))

(em/defaction fade-out-photo [id]
  [id]
  (effects/chain (effects/fade-out 500)
                 (ef/remove-node)))

(em/defaction fade-in-photo [id]
  [id]
  (effects/chain (effects/fade-in 1000)))

(register-listener destroy-listeners (fn [item]
                                       (if (not (nil? item))
                                         (fade-out-photo (get-photo-id item)))))

(register-listener add-listeners (fn [item]
                                   (if (not (nil? item))
                                     (fade-in-photo (get-photo-id (insert-photo-node item))))))

(def photo-queue (create-buffer 25))

; Recur through photos with a 50ms delay
(defn append-photos [response]
  (let [r (first response)
        photo (conj r {:counter @item-counter})]
    (when-not (nil? r)
      (photo-queue photo)
      (swap! item-counter inc))
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
  (fetch-tag (dommy/value (sel1 :#tag-input)))
  (.log js/console e))

(dommy/listen! (sel1 :#tag-submit) :click submit-handler)

(if (nil? (.-Flicky js/window))
  (GET "/tags" {:handler handler
                :error-handler error-handler})
  (handler (js->clj (.-Flicky js/window))))
