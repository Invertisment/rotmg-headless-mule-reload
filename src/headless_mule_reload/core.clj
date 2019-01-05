(ns headless-mule-reload.core
  (:require [clj-http.client :as client]
            [cheshire.core :as cheshire]))

(def max-fetch-times 3)
(def tsecond 1000)
(def request-initial-backoff (* 10 tsecond))
(def request-subsequent-backoff (* 10 (* 60 tsecond)))

(defn make-req [guid password]
  (println (format "Reloading [%s]" guid))
  (client/get "https://realmofthemadgodhrd.appspot.com/char/list"
              {:query-params {:muleDump true
                              :__source "jakcodex-v950"
                              :guid guid
                              :password password
                              :ignore (+ 1000 (rand-int 9000))
                              :_ (System/currentTimeMillis)}
               :headers {"Accept"  ["text/plain, */*; q=0.01"]
                         "Origin" "null"
                         "User-Agent" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/71.0.3578.80 Chrome/71.0.3578.80 Safari/537.36"}}))

#_(make-req "aaaaa@gmail.com" "caaaaaaaaa")

(defn req-and-retry [err?-fn skip?-fn req-fn guid]
  (loop [output (req-fn)
         counter 0]
    (cond
      (< max-fetch-times counter) (println (format "Failed to fetch %s in %s times." guid max-fetch-times))
      (skip?-fn output) (println (format "Skipping %s: '%s'" guid (:body output)))
      (err?-fn output) (do
                         (println "Waiting 10 min")
                         (java.lang.Thread/sleep request-subsequent-backoff)
                         (println (format "Retrying [%s] %s" (inc counter) guid))
                         (recur (req-fn) (inc counter))))))

(defn skip? [{:keys [body]}]
  (or
   (re-matches #"(?i).*account in use.*" body)
   (re-matches #"(?i).*credentials not valid.*" body)))

#_(skip? {:body "<Error>Account in use (593 seconds until timeout)</Error>"})
#_(skip? {:body "<Error>Account credentials not valid</Error>"})

(defn err? [req-output]
  (and
   (->> req-output
        :body
        (re-matches #"(?i).*error.*"))
   (not (skip? req-output))))

#_(err? {:body "<Error>Account in use (593 seconds until timeout)</Error>"})
#_(err? {:body "<Error> is here"})

(defn read-mules [filename]
  (let [mules (->> (slurp filename)
                   (re-find #"accounts ?= ?(\{[^\}]*\})")
                   second
                   cheshire/decode)]
    (println (format "Loaded %s mules" (count mules)))
    mules))

#_(read-mules "accounts.js")

(defn gogogo [filename]
  (run!
   (fn [[guid password]]
     (req-and-retry err? skip? #(make-req guid password) guid)
     (println (format "Waiting 10 seconds"))
     (java.lang.Thread/sleep request-initial-backoff))
   (read-mules filename)))

#_(gogogo "accounts.js")

(defn -main
  ([filename]
   (gogogo filename))
  ([]
   (-main "accounts.js")))

