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
   [:li
    [:img {:src (get photo "url_m")
           :width (get photo "width_s")
           :height (get photo "height_s")
           :alt (get photo "owner")
           :title (get photo "title")}]]))

(defn append-photos [response]
  (doseq [photo (get response "photos")]
    (dommy/append! (sel1 :#photos) (image-template photo))))

(defn append-tags [response]
  (dommy/append! (sel1 :h1) (string/join "," (get response "tags"))))

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
