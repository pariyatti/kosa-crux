(ns kuti.fixtures.time-fixtures
  (:require [kuti.support.time :as time]))

(defn freeze-clock
  ([t]
   (time/freeze-clock!)
   (t)
   (time/unfreeze-clock!))
  ([date-time test-fn]
   (time/freeze-clock! date-time)
   (test-fn)
   (time/unfreeze-clock!)))

(def win95 (time/instant "1995-08-24T00:00:00.000Z"))

(defn freeze-clock-1995 [t]
  (time/freeze-clock! win95)
  (t)
  (time/unfreeze-clock!))
