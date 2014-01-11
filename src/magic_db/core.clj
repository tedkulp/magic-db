(ns magic-db.core
  (:use hickory.core)
  (:require [hickory.select :as s])
  (:require [org.httpkit.client :as http])
  (:require [clojure.data.json :as json])
  (:require [clojure.string :only trim :as str]))

(defn pull-html-for-card [card-id]
  (:body @(http/get (str "http://gatherer.wizards.com/Pages/Card/Details.aspx?multiverseid=" card-id))))

(defn get-parsed-card-html [card-id]
  (as-hickory (parse (pull-html-for-card card-id))))

(defn add-id-prefix [string]
  (str "ctl00_ctl00_ctl00_MainContent_SubContent_SubContent_" string))

(defn select-content [parsed-html & args]
  (s/select
    (apply s/descendant args) parsed-html))

(defn extract-content [content]
  (:content
    (first content)))

(defn extract-first-content [content]
  (let [x (first (extract-content content))]
    (if (nil? x)
      x
      (clojure.string/trim x))))

(defn extract-flavor [content]
  (remove map? (map first (map :content (select-content content (s/id (add-id-prefix "FlavorText")) (s/or (s/class "cardtextbox") (s/tag "i")))))))

(defn convert-number [input]
  (let [x (read-string input)]
    (if (nil? x)
      input
      x)))

(defn extract-data [card-id]
  (let [parsed-html (get-parsed-card-html card-id)]
  {
   ; :html parsed-html
   :id card-id
   :name (extract-first-content (select-content parsed-html (s/id (add-id-prefix "nameRow")) (s/class "value")))
   :type (extract-first-content (select-content parsed-html (s/id (add-id-prefix "typeRow")) (s/class "value")))
   :converted (convert-number (extract-first-content (select-content parsed-html (s/id (add-id-prefix "cmcRow")) (s/class "value"))))
   :number (convert-number (extract-first-content (select-content parsed-html (s/id (add-id-prefix "numberRow")) (s/class "value"))))
   :pt (extract-first-content (select-content parsed-html (s/id (add-id-prefix "ptRow")) (s/class "value")))
   :rarity (extract-first-content (select-content parsed-html (s/id (add-id-prefix "rarityRow")) (s/class "value") (s/tag "span")))
   :artist (extract-first-content (select-content parsed-html (s/id (add-id-prefix "ArtistCredit")) (s/tag "a")))
   :flavor (extract-flavor parsed-html)
  }))

(defn -main []
  (println (json/write-str (extract-data 135194)))
  (println (json/write-str (extract-data 106426))))
