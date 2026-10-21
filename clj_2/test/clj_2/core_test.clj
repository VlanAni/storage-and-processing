(ns clj_2.core-test
  (:use clj_2.core)
  (:require [clojure.test :refer [deftest is]]))

(defn is-composite [n]
  (if (< n 4)
    false
    (reduce
      (fn [acc divisor]
        (or acc (zero? (mod n divisor))))
      false
      (range 2 (inc (int (Math/sqrt n)))))))

(deftest first-ten-primes
  (is (= '(2 3 5 7 11 13 17 19 23 29)
         (take 10 primes))))

(deftest empty-sequence
  (is (= ()
         (take 0 primes))))

(deftest first-20-primes
  (let [numbers (take 20 primes)]
    (is (= 20 (count numbers)))
    (is (not-any? is-composite numbers))))

(deftest first-50-primes
  (let [numbers (take 50 primes)]
    (is (= 50 (count numbers)))
    (is (not-any? is-composite numbers))))

(deftest first-100-primes
  (let [numbers (take 100 primes)]
    (is (= 100 (count numbers)))
    (is (not-any? is-composite numbers))))
