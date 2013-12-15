(ns codegumi.core
  (:require
   [codegumi.photo :as photo]
   [clojure.string :as string]
   [goog.object :as gobj]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Global mutable values

(def window-dimensions (atom {:width (.-innerWidth js/window) :height (.-innerHeight js/window)}))
(def item-counter (atom 0)) ; used to generate IDs for photos
(def random-play (atom 1))
(def timeout-id (atom 0))
(def buffer-length 25) ; Size of photo queue
(def ul (dom/by-id "photos")) ; Photo UL

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Enfocus animations for photos

(em/defaction fade-out-photo [id]
  [id]
  (effects/fade-out (+ 1000 (rand 500))
                    (fn [node] (dom/destroy! node))))

(em/defaction fade-in-photo [id]
  [id]
  (effects/chain (effects/fade-in (+ 1000 (rand 500)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Photo Queue (core.async)

(defn make-li [photo id]
  (let [lid (str "photo_" id)
        node (photo/build-photo-node photo @window-dimensions (fn [] (fade-in-photo (str "#" lid))))]
    (dom/append! ul node)))

(defn remove-li [id]
  (let [lid (str "photo_" id)]
    (fade-out-photo (str "#" lid))))

(defn load-squares [response]
  (let [c (chan buffer-length)
        r (get response "photos")]
    (doseq [i r]
      (put! c (conj i {:counter @item-counter}))
      (swap! item-counter inc))
    c))

(defn render-squares [c]
  (go
   (while true
     (let [p (<! c)
           id (:counter p)
           offset (- @item-counter id)
           floor (- @item-counter buffer-length)]
       (make-li p id)
       (remove-li (- floor offset))
       (<! (timeout 10))))))

(defn append-tags [response]
  (ef/at ".title-tag" (ef/content (string/join "," (get response "tags")))))

(defn handler [response]
  (render-squares (load-squares response))
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
  "Clear out any existing timeouts before starting a new one"
  (when (= 1 @random-play)
    (fetch-random)
    (js/clearTimeout @timeout-id)
    (reset! timeout-id (js/setTimeout #(check-interval) 10000))))

(defn submit-handler [e]
  (reset! random-play 0)
  (fetch-tag (ef/from "#tag-input" (ef/get-prop :value))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Drag & Drop courtesy of core.async

(defn calc-offset [evt ctr offset]
  (let [x (str (- (.-clientX evt) (first offset)) "px")
        y (str (- (.-clientY evt) (second offset)) "px")]
    [x y]))

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
                             (fn [e] (.preventDefault e) (put! out [msg e]))))
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
          ctr (photo/get-photo-center li)
          newz (inc (photo/get-highest-zindex "photo-li"))
          offsets [(.-offsetX evt) (.-offsetY evt)]
          mouse (listen js/document :mousemove :mouse)
          up (listen js/document :mouseup :up)
          chan (merge-chans mouse up)]
      (dom/add-class! li "drag")
      (dom/set-styles! li {:z-index newz})
      (go
       (loop [l li]
         (let [[msg-name msg-data] (<! chan)
               [ox oy] (calc-offset msg-data ctr offsets)]
           (if (= msg-name :up)
             (stop-drag l chan)
             (do (dom/set-styles! l {:top oy, :left ox})
                 (recur l)))))))))

(let [mouse (listen "#photos" :mousedown :down)]
  "Listen to any mousedown in the document"
  (go (while true
        (let [e (<! mouse)]
          (start-drag e)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; DOM Event listeners

(defn dispatch-click [[msg evt]]
  (let [id (dom/attr (.-target evt) "id")]
    (cond
     (= id "btn-pause") (reset! random-play 0)
     (= id "tag-submit") (submit-handler evt)
     (= id "btn-play") (do
                         (reset! random-play 1)
                         (check-interval))
     :else evt)))

(let [click (listen ".tag-form-button" :click :click)]
  "Listen to any clicke events in the document"
  (go (while true
        (let [e (<! click)]
          (dispatch-click e)))))

;; When window is resized, recalc window-dimensions to new value
(ef/at js/window (events/listen :resize
                                (fn []
                                  (reset! window-dimensions
                                          {:width (.-innerWidth js/window) :height (.-innerHeight js/window)}))))

(when (nil? (.-Flicky js/window))
  (check-interval))
