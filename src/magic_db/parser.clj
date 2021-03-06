(ns magic-db.parser
  (:use hickory.core)
  (:require [hickory.select :as s])
  (:require [org.httpkit.client :as http])
  (:require [clojure.string :only trim :as str]))

(defn pull-html-for-card [card-id]
  (:body @(http/get (str "http://gatherer.wizards.com/Pages/Card/Details.aspx?multiverseid=" card-id))))

(defn get-parsed-card-html [card-id]
  (as-hickory (parse (pull-html-for-card card-id))))

(defn side-indicator [side]
  (case side
    :front 0
    :back  1))

(defn compress-string [coll]
  (when-let [[f & r] (seq coll)]
    (if (= f (first r))
      (compress-string r)
      (cons f (compress-string r)))))

(defn clean-string [coll]
  (apply str (compress-string (str/trim coll))))

(defn side-id [side]
  (s/attr :id #(.endsWith % (str "cardComponent" (side-indicator side)))))

(defn id-prefix [string]
  (s/attr :id #(.endsWith % string)))

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

(defn extract-flavor [content side]
  ; Only grab the immediately nested text items. Ignore nested 'i', 'strong', etc tags as they just duplicate the text
  (map clean-string
    (remove map?
      (map first
        (map :content (select-content content (side-id side) (id-prefix "FlavorText") (s/or (s/class "cardtextbox") (s/tag "i"))))))))

(defn get-node-content [item]
  (cond
    (string? item) item
    (not (nil?
      (:alt (:attrs item)))) (str "{{" (:alt (:attrs item)) "}}")
    :else nil))

(defn get-text-lines [item]
  (doall
    (remove empty?
      (remove nil?
        (map get-node-content item)))))

(defn extract-text [content side]
  (let [tree (select-content content (side-id side) (id-prefix "textRow") (s/class "value") (s/class "cardtextbox"))]
    (map clean-string (map #(apply str (interpose " " %)) (map get-text-lines (map :content tree))))))

(defn extract-set-from-side [content side]
  (first
    (:content
      (last
        (s/select
          (s/descendant
            (s/attr :id #(.endsWith % "setRow")) (s/tag "a")) content)))))

(defn has-back? [parsed-html]
  (>
   (count
     (s/select
       (s/descendant
         (s/attr :id #(.endsWith % "subtitleDisplay"))) parsed-html)) 1))

(defn extract-side-data [card-id side parsed-html]
  {
   :name (clean-string (extract-first-content (select-content parsed-html (side-id side) (id-prefix "nameRow") (s/class "value"))))
   :type (clean-string (extract-first-content (select-content parsed-html (side-id side) (id-prefix "typeRow") (s/class "value"))))
   :converted (extract-first-content (select-content parsed-html (side-id side) (id-prefix "cmcRow") (s/class "value")))
   :number (extract-first-content (select-content parsed-html (side-id side) (id-prefix "numberRow") (s/class "value")))
   :pt (extract-first-content (select-content parsed-html (side-id side) (id-prefix "ptRow") (s/class "value")))
   :rarity (extract-first-content (select-content parsed-html (side-id side) (id-prefix "rarityRow") (s/class "value") (s/tag "span")))
   :artist (extract-first-content (select-content parsed-html (side-id side) (id-prefix "ArtistCredit") (s/tag "a")))
   :text (extract-text parsed-html side)
   :flavor (extract-flavor parsed-html side)
   ;:colors
   ;:mana
   ;:imageurl
   ;:othersets
  })

(defn get-card-data [card-id]
  (let [
      parsed-html (get-parsed-card-html card-id)
      result {:id card-id :setname (extract-set-from-side parsed-html :front) :front (extract-side-data card-id :front parsed-html)}
    ]
    (if (has-back? parsed-html)
      (assoc result :back (extract-side-data card-id :back parsed-html))
      result)))
