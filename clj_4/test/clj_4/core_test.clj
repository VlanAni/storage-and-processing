(ns clj_4.core-test
  (:use clj_4.core)
  (:require [clojure.test :refer [deftest is]]))

(def take-from-storage #'clj_4.core/take-from-storage)

(defn wait-until
  [pred timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (cond (pred) true
            (> (System/currentTimeMillis) deadline) false
            :else (do (Thread/sleep 10) (recur))))))

(defn new-counter [n]
  (atom n :validator #(>= % 0)))

(deftest take-takes-at-most-needed
  (let [a (new-counter 10)]
    (is (= 4 (take-from-storage a 4)))
    (is (= 6 @a))))

(deftest take-takes-only-what-is-available
  (let [a (new-counter 3)]
    (is (= 3 (take-from-storage a 10)))
    (is (= 0 @a))
    (is (= 0 (take-from-storage a 5)))))

(deftest take-is-safe-under-contention
  (let [a      (new-counter 50)
        taken  (atom 0)
        thr    (doall (for [_ (range 100)]
                        (future (swap! taken + (take-from-storage a 1)))))]
    (doseq [t thr] @t)                       
    (is (= 50 @taken))
    (is (= 0 @a))))

(defn fresh-state 
  []
  (let [target (storage "Product" 0)
        f      (factory 2 10 target "Ore" 3)]
    [@(:worker f) target]))

(deftest not-enough-wares-no-production
  (let [[state target] (fresh-state)
        ore   (new-counter 2)
        state' (notify-msg state "Ore" ore 2)]
    (is (= {"Ore" 2} (:buffer state')))
    (is (= 0 @ore))
    (await (:worker target))
    (is (= 0 @(:storage target)))))

(deftest takes-only-what-is-needed
  (let [[state _] (fresh-state)
        ore    (new-counter 10)
        state' (notify-msg state "Ore" ore 10)]
    (is (= 7 @ore))))

(deftest full-cycle-produces-and-clears-buffer
  (let [[state target] (fresh-state)
        ore    (new-counter 5)
        state' (notify-msg state "Ore" ore 5)]
    (is (= {"Ore" 0} (:buffer state')))
    (is (= 2 @ore))
    (is (wait-until #(= 2 @(:storage target)) 1000)
        )))

(deftest unknown-ware-is-ignored
  (let [[state _] (fresh-state)
        other  (new-counter 5)
        state' (notify-msg state "Gold" other 5)]
    (is (= (:buffer state) (:buffer state')))
    (is (= 5 @other))))

(deftest both-wares-are-produced-despite-shortage
  (let [metal-st (storage "Metal" 0)
        gears-st (storage "Gears" 0)
        metal-f  (factory 1 10 metal-st "Ore" 4)
        gears-f  (factory 1 10 gears-st "Ore" 2)
        ore-st   (storage "Ore" 0 metal-f gears-f)]
    (dotimes [_ 60]
      (send (:worker ore-st) supply-msg 2)
      (Thread/sleep 5))
    (is (wait-until #(and (pos? @(:storage metal-st))
                          (pos? @(:storage gears-st)))
                    5000)
        )
    (is (nil? (agent-error (:worker metal-f))))
    (is (nil? (agent-error (:worker gears-f))))
    (is (nil? (agent-error (:worker ore-st))))))