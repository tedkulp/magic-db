(ns magic-db.core
  (:use clojure.java.io)
  (:require [clojure.data.json :as json])
  (:require [magic-db.parser :as parser]))

(defn -main []
  (with-open [rdr (reader "ids/M14.txt")]
    (doseq [line (line-seq rdr)]
      (println (json/write-str (parser/get-card-data line))))))
  ; (println (json/write-str (parser/get-card-data 135194)))
  ; (println (json/write-str (parser/get-card-data 106426)))
  ; (println (json/write-str (parser/get-card-data 242509)))
  ; (println (json/write-str (parser/get-card-data 107387))))
