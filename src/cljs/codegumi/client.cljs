(ns codegumi.core
  (:require
    [clojure.string :as string]
    [dommy.utils :as utils]
    [dommy.core :as dommy]
    [ajax.core :refer [GET POST]])
  (:use-macros
   [dommy.macros :only [node sel sel1]]))

(defn ^:export log [thing] (.log js/console (clj->js thing)))

(aset js/window "log" log)

(defn image-template [photo]
  (node
   [:li {:id (string/join "_" ["photo" (get photo "id")])}
    [:img {:src (get photo "url_m")
           :width (get photo "width_s")
           :height (get photo "height_s")
           :alt (get photo "owner")
           :title (get photo "title")}]]))

; Atoms to hold our photos and listeners
(def plist (atom []))
(def destroy-listeners (atom []))
(def add-listeners (atom []))

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

(register-listener destroy-listeners (fn [item]
                                       (.log js/console (get item "title"))
                                       (dommy/remove! (sel1 (string/join "_" ["#photo" (get item "id")])))))

(register-listener add-listeners (fn [item]
                                   (dommy/append! (sel1 :#photos) (image-template item))))

(def photo-queue (create-buffer 25))

; Instead of doseq try and run a setTimeout using a recursive function
; with an atom as the local var (let [r (atom [])) to hold the
; reducing sequence
(defn append-photos [response]
  (doseq [photo (get response "photos")]
    (photo-queue photo)))

(defn append-tags [response]
  (dommy/set-text! (sel1 ".title-tag") (string/join "," (get response "tags"))))

(defn handler [response]
  (aset js/window "response" response)
  (append-photos response)
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
