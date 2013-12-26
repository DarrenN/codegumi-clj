(ns codegumi.handler
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [clojure.string :as string]
            [codegumi.flickr :as flickr]
            [codegumi.views :as views]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body data})

(defroutes app-routes
  (GET "/" [] (views/page-template))

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
  (handler/site app-routes))
