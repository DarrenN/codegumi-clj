(ns codegumi.core
  (:require
   [codegumi.photo :as photo]
   [clojure.string :as string]
   [goog.object :as gobj]
   [goog.fx.dom :as fx-dom]
   [goog.events :as gevents]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Global immutable values

(def buffer-length 25) ; Size of photo queue
(def ul (dom/by-id "photos")) ; Photo UL

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Animations for photos

(defn destroy-event [evt]
  "Nuclear detonation of Event to allow for GC"
  (let [anim (.-anim evt)
        evtarget (.-target evt)]
    (.dispose evtarget)
    (.dispose anim)
    (.dispose evt)
    (gobj/clear anim)
    (gobj/clear evt)))

(defn destroy-li-el [evt]
  (let [node (.-element (.-anim evt))]
    (dom/destroy! node)
    (destroy-event evt)))

(defn fade-out-photo [id]
  "Doing this manually because Enfocus doesn't properly dispose of the Event"
  (let [node (dom/by-id id)
        anim (fx-dom/FadeOut. node (+ 1000 (rand 500)))]
    (when-not (nil? node)
      (gevents/listen anim js/goog.fx.Animation.EventType.END destroy-li-el)
      (. anim (play)))))

(defn fade-in-photo [id]
  (let [anim (fx-dom/FadeIn. (dom/by-id id) (+ 1000 (rand 500)))]
    (. anim (play))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Form manipulation

(defn remove-loading-state! []
  (let [input (dom/by-id "tag-input")
        inode (dom/nodes (css/sel "#tag-submit > i"))]
    (dom/remove-attr! input :disabled)
    (doseq [class ["fa-spin" "fa-cog"]] (dom/remove-class! inode class))
    (dom/add-class! inode "fa-search")))

(defn set-loading-state! []
  (let [input (dom/by-id "tag-input")
        inode (dom/nodes (css/sel "#tag-submit > i"))]
    (dom/set-attr! input :disabled)
    (dom/remove-class! inode "fa-search")
    (dom/add-class! inode "fa-cog fa-spin")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Photo Queue (core.async)

(defn make-li [photo id]
  (let [lid (str "photo_" id)
        node (photo/build-photo-node photo @window-dimensions (fn [] (fade-in-photo lid)))]
    (dom/append! ul node)))

(defn remove-li [id]
  (let [lid (str "photo_" id)]
    (fade-out-photo lid)))

(defn load-squares [response]
  (let [c (chan buffer-length)
        r (get (js->clj response) "photos")]
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
  (let [r (js->clj response)
        tstr (string/join ", " (get r "tags"))]
    (ef/at ".title-tag" (ef/content tstr))
    (ef/at "title" (ef/content (str (string/capitalize tstr) " | CodeGumi | 符組")))
    (ef/at ".tag-input" (ef/do->
                         (ef/set-attr :value " ")
                         (ef/remove-attr :value)
                         (ef/set-attr :placeholder (str "Current tag: " tstr))))))

(defn set-history-state [response]
  "Set the url to the current tags using history.pushState"
  (let [r (js->clj response)
        history (.-history js/window)
        tags (clojure.string/join " " (get r "tags"))]
    (.pushState history r tags (str "/tags/" tags))))

(defn remove-xhr-listeners! []
  "After every XHR we need to remove the complete listeners from the goog.events.listeners_ stack"
  (let [events (-> js/window .-goog .-events)
        listeners (js->clj (.-listeners_ events) :keywordize-keys true)]
    (doseq [listener listeners]
      (let [[key l] listener]
        (if (= (.-type l) "complete")
          (.unlistenByKey events (.-key l)))))))

(defn handler [response]
  "Take the response and send it along its way"
  (remove-loading-state!)
  (render-squares (load-squares response))
  (append-tags response)
  (set-history-state response)
  (remove-xhr-listeners!))

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text)))

(defn fetch-random []
  (set-loading-state!)
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
  (set-loading-state!)
  (fetch-tag (ef/from "#tag-input" (ef/get-prop :value)))
  (reset! random-play 0))

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
  ;; It is very important to manually clear these listeners because
  ;; otherwise they will stack up in memory as detached elements
  (ef/at js/document (events/remove-listeners :mousemove :mouseup))
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
             (do (stop-drag l chan)
                 (.dispose evt)) ; manually dispose of Event
             (do (dom/set-styles! l {:top oy, :left ox})
                 (.dispose evt) ; manually dispose of Event
                 (recur l)))))))))

(let [mouse (listen "#photos" :mousedown :down)]
  "Listen to any mousedown in the document"
  (go (while true
        (let [e (<! mouse)]
          (start-drag e)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; DOM Event listeners

(defn dispatch-click [[msg evt]]
  (let [id (dom/attr (.-currentTarget evt) "id")]
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

(if (nil? (.-Flicky js/window))
  (check-interval)
  (handler (.-Flicky js/window)))
