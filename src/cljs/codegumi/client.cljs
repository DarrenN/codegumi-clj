(ns codegumi.core
  (:require
   [codegumi.buffer :as buffer]
   [codegumi.photo :as photo]
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

; Global mutable values
(def window-dimensions (atom {:width (.-innerWidth js/window) :height (.-innerHeight js/window)}))
(def item-counter (atom 0))
(def random-play (atom 1))

; When window is resized, recalc window-dimensions to new value
(ef/at js/window (events/listen :resize (fn [] (reset! window-dimensions {:width (.-innerWidth js/window) :height (.-innerHeight js/window)}))))

(em/defaction fade-out-photo [id]
  [id]
  (effects/chain (effects/fade-out (+ 1000 (rand 500)))
                 (ef/remove-node)))

(em/defaction fade-in-photo [id]
  [id]
  (effects/chain (effects/fade-in (+ 1000 (rand 500)))))

(defn on-item-add [item]
  (when-not (nil? item)
    (dommy/append! (sel1 :#photos)
                   (photo/build-photo-node item
                                           @window-dimensions
                                           (fn [] (fade-in-photo (photo/get-photo-id item)))))))

(defn on-item-remove [item]
  (when-not (nil? item)
    (fade-out-photo (photo/get-photo-id item))))

(buffer/register-listener buffer/destroy-listeners on-item-remove)
(buffer/register-listener buffer/add-listeners on-item-add)

; Create the main sliding buffer
(def photo-queue (buffer/create-buffer 25))

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

(defn fetch-random []
  (GET "/tags" {:handler handler
                :error-handler error-handler
                :headers {:Accept "application/json"}}))

(defn fetch-tag [tag]
  (GET (str "/tags/" tag) {:handler handler
                           :error-handler error-handler
                           :headers {:Accept "application/json"}}))

(defn check-interval []
  (when (= 1 @random-play)
    (fetch-random)
    (js/setTimeout #(check-interval) 10000)))

(defn submit-handler [e]
  (.preventDefault e)
  (reset! random-play 0)
  (fetch-tag (dommy/value (sel1 :#tag-input))))

;(dommy/listen! (sel1 :#tag-submit) :click submit-handler)

(ef/at "#tag-submit" (events/listen :click submit-handler))

; Pause loading random images
(ef/at "#btn-pause" (events/listen :click
                                   (fn [e]
                                     (.preventDefault e)
                                     (reset! random-play 0))))

; Restart random image loads
(ef/at "#btn-play" (events/listen :click
                                 (fn [e]
                                   (.preventDefault e)
                                   (reset! random-play 1)
                                   (check-interval))))

(when (nil? (.-Flicky js/window))
  (check-interval))
