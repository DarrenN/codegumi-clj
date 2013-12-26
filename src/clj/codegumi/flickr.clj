(ns codegumi.flickr
  (:require [clj-http.client :as http]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [cheshire.core :refer :all]
            [environ.core :as environ]
            [me.raynes.fs :as fs]
            [clj-time.core :as time]
            [clj-time.coerce :as timec]
            [clj-time.local :as timel]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

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

;; Tags to pull when we have no data
(def default-tags ["yamanote" "akihabara" "osaka" "kabukicho" "roppongi" "ginza" "asakusa"])

;; How many days before and after todays date to keep a photo file
(def days-fresh 2)

(def public-files (file-seq (io/file (io/resource "public/json"))))

(defn only-files [files-s]
  (filter #(.isFile %) files-s))

(defn file-names [files-s]
  "Return vector of file names"
  (map #(.getName %) files-s))

(defn only-json [files-s]
  "Return only .json files"
  (filter #(re-find #"json" %) files-s))

(defn search-json
  "Make a Flickr request for files"
  [params]
  (http/get (:url api) {:query-params (merge default-params {"method" (:search api), "nojsoncallback" 1} params)
                                       :as :json
                                       :accept :json
                                       :throw-exceptions false}))

(defn make-tags [tags]
  "Create a clean vector of tags"
  (if (vector? tags)
    (vec (remove empty? tags))
    (string/split (string/trim tags) #" ")))

(defn is-mtime-valid? [file]
  "Check the mtime of the json and file and determine if it falls within a time interval"
  (let [mtime (timec/from-long (fs/mod-time file))
        min (time/minus (timel/local-now) (time/days days-fresh))
        max (time/plus (timel/local-now) (time/days days-fresh))]
    (time/within? (time/interval min max)
                  mtime)))

(defn gc-cache-file! [file]
  "If the file is expired delete it."
  (when-not (is-mtime-valid? file)
    (when (= (int (rand 3)) 2)
      (fs/delete file))))

(defn make-tags-from-file [file]
  "Convert filename into tag vector"
  (-> file
      (fs/base-name true)
      string/trim
      (string/split #"_")))

(defn make-filename-from-tags [tags]
  "Vector into a complete filename"
  (let [t (make-tags tags)]
    (string/join [(io/resource "public/json/") (string/join "_" t) ".json"])))

(defn search-tags
  [tags]
  (let [json (search-json {"tags" (string/join "," tags)})]
    (when (== (:status json) 200)
      (get-in json [:body :photos :photo]))))

(defn save-photo-json
  "Pull photos from Flickr and stash in a cache file (.json)"
  [filename tags]
  (let [photos (search-tags tags)]
    (when-not (nil? photos)
      (let [photo-json (generate-string {:tags tags, :photos photos} {:escape-non-ascii true})]
        (spit filename photo-json)
        photo-json))))

(defn get-photos
  "If tags are not cached or cache expires then pull a fresh set from Flickr"
  [tags]
  (let [t (make-tags tags)
        filename (make-filename-from-tags t)]
    (gc-cache-file! (io/file filename))
    (if (.exists (io/file filename))
      (slurp filename)
      (save-photo-json filename t))))

(defn get-default-sets []
  "Load our default tags into the file cache then execute callback"
  (doseq [t default-tags]
    (get-photos t)))

(defn get-random-photos []
  "Pull a random photo set from /json, if there are no files, then pull a yamanote set"
  (let [files (-> (file-seq (io/file (io/resource "public/json")))
                  only-files
                  file-names
                  only-json)]
    (if (empty? files)
      (get-default-sets)
      (let [filepath (io/file (io/resource (str "public/json/" (rand-nth files))))]
        (gc-cache-file! filepath)
        (if (.exists filepath)
          (slurp filepath)
          (get-photos (make-tags-from-file filepath)))))))
