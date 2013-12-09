(ns codegumi.core
  (:require
   [codegumi.buffer :as buffer]
   [codegumi.photo :as photo]
   [clojure.string :as string]
   [enfocus.core :as ef]
   [enfocus.events :as events]
   [enfocus.effects :as effects]
   [domina :as dom]
   [domina.css :as css]
   [ajax.core :refer [GET POST]]
   [cljs.core.async :refer (<! >! chan put! take! alts! timeout close! dropping-buffer sliding-buffer)]
   [clojure.browser.repl :as repl])
  (:require-macros
   [enfocus.macros :as em]
   [cljs.core.async.macros :refer (go alt!)]))

(repl/connect "http://localhost:9000/repl")

(defn ^:export log [thing] (.log js/console (clj->js thing)))
(aset js/window "log" log)

; Global mutable values
(def window-dimensions (atom {:width (.-innerWidth js/window) :height (.-innerHeight js/window)}))
(def item-counter (atom 0))
(def random-play (atom 1))
(def timeout-id (atom 0))

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
    (ef/at "#photos" (ef/append (photo/build-photo-node item
                                                        @window-dimensions
                                                        (fn [] (fade-in-photo (photo/get-photo-id item))))))))

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
  (ef/at ".title-tag" (ef/content (string/join "," (get response "tags")))))

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
  ; Clear out any existing timeouts before starting a new one
  (when (= 1 @random-play)
    (fetch-random)
    (js/clearTimeout @timeout-id)
    (reset! timeout-id (js/setTimeout #(check-interval) 10000))))

(defn submit-handler [e]
  (.preventDefault e)
  (reset! random-play 0)
  (fetch-tag (ef/from "#tag-input" (ef/get-prop :value))))

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


(defn extract-photo-center [li]
  "Return a tuple of the x y center point of the img"
  (let [img (dom/nodes (css/sel li "img"))
        xy [(/ (dom/attr img :width) 2) (/ (dom/attr img :height) 2)]]
    (map Math/floor xy)))

(defn get-highest-zindex [class]
  "Return the highest zIndex in a stack of photos"
  (let [li (map (fn [l] (.parseInt js/window (dom/style l :z-index))) (dom/nodes (dom/by-class class)))]
    (apply max 0 li)))

(defn merge-chans [& chans]
  (let [rc (chan)]
    (go
     (loop []
       (put! rc (first (alts! chans)))
       (recur)))
    rc))

(defn listen [el type msg]
  "Send events on el to a channel, with msg as a label"
  (let [out (chan)]
    (ef/at el (events/listen type
                             (fn [e] (put! out [msg e]))))
    out))

(defn stop-drag [li chan]
  "Cleanup the channels and remove styling so we can change appearance"
  (close! chan)
  (dom/remove-class! li "drag")
  li)

(defn start-drag [[msg evt]]
  "Creates a channel to handle drag/drop of photos in the browser"
  (when (dom/has-class? (.-target evt) "image-mask")
    (reset! random-play 0) ; stop the cycling
    (let [div (.-target evt)
          li (.-parentNode div)
          ctr (extract-photo-center li)
          newz (inc (get-highest-zindex "photo-li"))
          mouse (listen js/document :mousemove :mouse)
          up (listen js/document :mouseup :up)
          chan (merge-chans mouse up)]
      (dom/add-class! li "drag")
      (dom/set-styles! li {:z-index newz})
      (go
       (loop [l li]
         (let [[msg-name msg-data] (<! chan)]
           (when (= msg-name :up)
             (stop-drag l chan))
           (when-not (= msg-name :up)
             (dom/set-styles! l {:top (str (- (.-clientY msg-data) (second ctr)) "px"), :left (str (- (.-clientX msg-data) (first ctr)) "px")})
             (recur l))))))))

(let [mouse (listen js/document :mousedown :down)]
  (go (while true
        (let [e (<! mouse)]
          (start-drag e)))))

(when (nil? (.-Flicky js/window))
  (check-interval))
