(ns clj_3.core-test
  (:use clj_3.core)
  (:require [clojure.test :refer [deftest is]]))

(deftest matches-regular-filter
  (is (= (filter even? (range 1 100))
         (p-filter even? 3 7 (range 1 100)))))

(deftest empty-input
  (is (empty? (p-filter even? 2 5 []))))

(deftest nothing-matches
  (is (empty? (p-filter neg? 2 5 (range 50)))))

(deftest everything-matches
  (is (= (range 37) (p-filter (constantly true) 3 4 (range 37)))))

(deftest block-larger-than-collection
  (is (= [2 4] (p-filter even? 4 1000 [1 2 3 4 5]))))

(deftest size-not-multiple-of-block
  (is (= (filter odd? (range 23))
         (p-filter odd? 2 5 (range 23)))))

(deftest order-is-preserved
  (let [xs (shuffle (range 200))]
    (is (= (filter #(> % 100) xs)
           (p-filter #(> % 100) 4 9 xs)))))

(deftest infinite-first-block
  (is (= [0 2] (take 2 (p-filter even? 1 3 (range))))))

(deftest infinite-first-batch-and-beyond
  (is (= [1 3 5 7 9 11 13 15 17 19]
         (take 10 (p-filter odd? 2 5 (iterate inc 0))))))

(deftest infinite-sparse-matches-skip-empty-batches
  (is (= [0 1000 2000]
         (take 3 (p-filter #(zero? (mod % 1000)) 4 10 (range))))))