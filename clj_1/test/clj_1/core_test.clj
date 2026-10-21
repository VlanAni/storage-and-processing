(ns clj_1.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clj_1.core :refer [get_all_words]]))

(deftest words-of-length-zero
  (testing
    (is (= '("") (get_all_words '("a" "b" "c") 0)))))

(deftest words-of-length-one
  (testing 
    (is (= '("a" "b" "c")
           (get_all_words '("a" "b" "c") 1)))))

(deftest words-of-length-two
  (testing 
    (is (= #{"ab" "ac" "ba" "bc" "ca" "cb"}
           (set (get_all_words '("a" "b" "c") 2))))))

(deftest words-of-length-three
  (testing
    (let [words (get_all_words '("a" "b" "c") 3)]
      (is (= 12 (count words)))
      (is (= 12 (count (set words))))
      (is (every? #(= 3 (count %)) words)))))

(deftest empty-alphabet
  (testing 
    (is (= '() (get_all_words '() 1)))
    (is (= '() (get_all_words '() 2)))
    (is (= '() (get_all_words '() 3)))))

(deftest single-letter-alphabet
  (testing 
    (is (= '("a") (get_all_words '("a") 1))))
  (testing 
    (is (= '() (get_all_words '("a") 2)))
    (is (= '() (get_all_words '("a") 3)))))

(deftest expected-number-of-words
  (testing 
    (let [alphabet '("a" "b" "c" "d")]
      (is (= 4 (count (get_all_words alphabet 1))))
      (is (= 12 (count (get_all_words alphabet 2))))
      (is (= 36 (count (get_all_words alphabet 3))))
      (is (= 108 (count (get_all_words alphabet 4)))))))