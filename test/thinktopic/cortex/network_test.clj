(ns thinktopic.cortex.network-test
  (:require
    [clojure.test :refer [deftest is are]]
    [clojure.core.matrix :as mat]
    [thinktopic.datasets.mnist :as mnist]
    [thinktopic.cortex.util :as util]
    [thinktopic.cortex.network :as net]))

; a	b	| a XOR b
; 1	1	     0
; 0	1	     1
; 1	0	     1
; 0	0	     0
(def XOR-DATA [[[1 1]] [[0 1]] [[1 0]] [[0 0]]])
(def XOR-LABELS [[[0]] [[1]] [[1]] [[0]]])

(defn xor-test
  []
  (let [net (net/sequential-network
              [(net/linear-layer :n-inputs 2 :n-outputs 3)
               (net/sigmoid-activation 3)
               (net/linear-layer :n-inputs 3 :n-outputs 1)])
        training-data XOR-DATA
        training-labels XOR-LABELS
        n-epochs 2000
        loss-fn (net/quadratic-loss)
        learning-rate 0.3
        momentum 0.9
        batch-size 1
        optimizer (net/sgd-optimizer net loss-fn learning-rate momentum)
        _ (net/train-network optimizer n-epochs batch-size training-data training-labels)
        [results score] (net/evaluate net XOR-DATA XOR-LABELS)
        label-count (count XOR-LABELS)
        score-percent (float(/ score label-count))]
    (println "NET: " net)
    (println "forward: "  (net/forward net [1 0]))
    (println (format "XOR Score: %f [%d of %d]" score-percent score label-count))
    nil))

(deftest confusion-test
  (let [cf (net/confusion-matrix ["cat" "dog" "rabbit"])
        cf (-> cf
            (net/add-prediction "dog" "cat")
            (net/add-prediction "dog" "cat")
            (net/add-prediction "cat" "cat")
            (net/add-prediction "cat" "cat")
            (net/add-prediction "rabbit" "cat")
            (net/add-prediction "dog" "dog")
            (net/add-prediction "cat" "dog")
            (net/add-prediction "rabbit" "rabbit")
            (net/add-prediction "cat" "rabbit")
            )]
    (net/print-confusion-matrix cf)
    (is (= 2 (get-in cf ["cat" "dog"])))))


(def trained* (atom nil))

(defn mnist-labels
  [class-labels]
  (let [n-labels (count class-labels)
        labels (mat/zero-array [n-labels 10])]
    (doseq [i (range n-labels)]
      (mat/mset! labels i (nth class-labels i) 1.0))
    labels))

(def MNIST-LABELS (mnist-labels @mnist/label-store))

(defn mnist-test
  [& [net]]
  (let [start-time (util/timestamp)
        training-data @mnist/data-store
        [n-inputs input-width] (mat/shape training-data)
        training-data (mat/submatrix training-data 0 100 0 input-width)
        training-data (map #(mat/broadcast % [1 input-width]) training-data)
        training-labels (mnist-labels @mnist/label-store)
        test-data @mnist/test-data-store
        test-labels (mnist-labels @mnist/test-label-store)
        net (or net (net/sequential-network
                      [(net/linear-layer :n-inputs 784 :n-outputs 30)
                       (net/sigmoid-activation 30)
                       (net/linear-layer :n-inputs 30 :n-outputs 10)
                       (net/sigmoid-activation 10)]))
        n-epochs 1
        learning-rate 3.0
        momentum 0.9
        batch-size 10
        loss-fn (net/quadratic-loss)
        optimizer (net/sgd-optimizer net loss-fn learning-rate momentum)
        setup-time (util/ms-elapsed start-time (util/timestamp))
        _ (println "setup time: " setup-time "ms")
        _ (net/train-network optimizer n-epochs batch-size training-data training-labels)
        _ (println "evaluating network...")
        [results score] (net/evaluate net test-data test-labels)
        label-count (first (mat/shape test-labels))
        score-percent (float (/ score label-count))]
    (reset! trained* net)
    (println (format "MNIST Score: %f [%d of %d]" score-percent score label-count))))

; Data from: Dominick Salvator and Derrick Reagle
; Shaum's Outline of Theory and Problems of Statistics and Economics
; 2nd edition,  McGraw-Hill, 2002, pg 157

; Predict corn yield from fertilizer and insecticide inputs
; [corn, fertilizer, insecticide]
(def CORN-DATA
  [[6  4]
   [10  4]
   [12  5]
   [14  7]
   [16  9]
   [18 12]
   [22 14]
   [24 20]
   [26 21]
   [32 24]])

(def CORN-LABELS
  [40 44 46 48 52 58 60 68 74 80])

(def CORN-RESULTS
  [40.32, 42.92, 45.33, 48.85, 52.37, 57.0, 61.82, 69.78, 72.19, 79.42])

(def model* (atom nil))

(defn regression-test
  [& [net]]
  (let [net (or net (net/sequential-network [(net/linear-layer :n-inputs 2 :n-outputs 1)]))
        loss (net/mse-loss)
        learning-rate 0.0001
        momentum 0.9
        optimizer (net/sgd-optimizer net loss learning-rate momentum)
        n-epochs 1000 batch-size 1
        data (map mat/row-matrix CORN-DATA)
        labels (map vector CORN-LABELS)
        results (map vector CORN-RESULTS)]
    (net/train-network optimizer n-epochs batch-size data labels)
    (reset! model* net)
    (println "After training the ideal values solving analytically are:
     corn = 31.98 + 0.65 * fertilizer + 1.11 * insecticides\n")
    (println "The networked learned:")
    (println "    corn = " (mat/mget (get-in net [:layers 0 :biases]) 0 0) "+ "
             (mat/mget (get-in net [:layers 0 :weights]) 0 0) "* x +"
             (mat/mget (get-in net [:layers 0 :weights]) 0 1) "* y")

    (println "text  :  prediction")
    (doseq [[label fertilizer insecticide] (map concat results CORN-DATA)]
      (println label " : " (mat/mget (net/forward net (mat/row-matrix [fertilizer insecticide])) 0 0)))
    ))
