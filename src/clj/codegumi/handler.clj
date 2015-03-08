(ns codegumi.handler
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.adapter.jetty :refer :all]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [clj-time.core :refer (years from-now)]
            [clj-time.coerce :as coerce]
            [ring.util.time :as ring-time]
            [ring.middleware.resource :refer :all]
            [ring.middleware.content-type :refer :all]
            [ring.middleware.not-modified :refer :all]
            [codegumi.flickr :as flickr]
            [codegumi.views :as views]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn never-expires []
  (-> 1
      years
      from-now
      coerce/to-date
      ring-time/format-date))

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body data})

(defroutes app-routes
  (GET "/" []
       {:headers {"Expires" (never-expires)
                  "Content-Type" "text/html; charset=utf-8"}
        :body (views/page-template)})

  (GET "/tags" [] (let [photos (flickr/get-random-photos)]
                    (if (nil? photos)
                      (json-response {:msg "Flickr did not respond"} 500)
                      (json-response photos))))

  (GET "/tags/:q" [q :as req]
       (info q)
       (if (= (get (:headers req) "accept") "application/json")
         (json-response (flickr/get-photos (string/split q #"\W")))
         (views/page-template (flickr/get-photos (string/split q #"\W")))))

  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (handler/site app-routes)
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))

(def cli-options
  ;; An option with a required argument
  [["-j" "--json PATH" "JSON folder"]
   ;; A boolean option defaulting to nil
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Codegumi.com"
        ""
        "Options:"
        options-summary
        ""]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main
  "Starts the application from uberjar"
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count options) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    (swap! flickr/opts conj options)
    (run-jetty app {:port 8080})))
