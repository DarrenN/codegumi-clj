(ns codegumi.flickr
  (:require [clj-http.client :as http]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [cheshire.core :refer :all]
            [environ.core :as environ]))

(def api {:url "http://api.flickr.com/services/rest/"
          :key (environ/env :flickr-api-key)
          :format "json"
          :search "flickr.photos.search"})

(def default-params {"api_key" (:key api)
                     "format" (:format api)
                     "sort" "interestingness-desc"
                     "content_type" 1
                     "extras" "description,owner_name,tags,path_alias,url_sq,url_t,url_s,url_q,url_m,url_n,url_z,url_c,url_l,url_o"
                     "per_page" 25
                     "tag_mode" "all"})

(defn search-json
  [params]
  (http/get (:url api) {:query-params (merge default-params {"method" (:search api), "nojsoncallback" 1} params)
                                       :as :json
                                       :accept :json
                                       :throw-exceptions false}))

(defn search-tags
  [tags]
  (let [json (search-json {"tags" (string/join "," tags)})]
    (when (== (:status json) 200)
      (get-in json [:body :photos :photo]))))

(defn save-photo-json
  [filename tags]
  (let [photos (search-tags tags)]
    (when-not (nil? photos)
      (let [photo-json (generate-string {:tags tags, :photos photos} {:escape-non-ascii true})]
        (spit filename photo-json)
        photo-json))))

(defn get-photos
  [tags]
  (let [filename (string/join [(io/resource "public/json/") (string/join "_" tags) ".json"])]
    (if (.exists (io/file filename))
      (slurp filename)
      (save-photo-json filename tags))))

(defn get-random-photos []
  (let [files (filter (fn [f] (re-find #"json" f)) (seq (.list (io/file (io/resource "public/json/")))))
        filename (rand-nth files)
        filepath (io/resource (str "public/json/" filename))]
    (if (.exists (io/file filepath))
      (slurp filepath)
      (get-random-photos))))
