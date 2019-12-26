package com.namsor.oss.classify.bayes;

import com.google.common.primitives.Longs;
import static com.namsor.oss.classify.bayes.AbstractNaiveBayesClassifierImpl.pathCategory;
import static com.namsor.oss.classify.bayes.AbstractNaiveBayesClassifierImpl.pathGlobal;

import java.io.*;
import java.util.HashMap;

import java.util.Map;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.WriteBatch;

/**
 * Naive Bayes Classifier implementation with Laplace smoothing and LevelDB as
 * key/value store. Learning is Synchronized but classification is not.
 *
 * @author elian carsenat, NamSor SAS
 */
public class NaiveBayesClassifierLevelDBLaplacedImpl extends AbstractNaiveBayesClassifierLevelDBImpl implements INaiveBayesClassifier {

    private static final boolean VARIANT = false;
    private static final double ALPHA = 1d;
    private final boolean variant;
    private final double alpha;

    public NaiveBayesClassifierLevelDBLaplacedImpl(String classifierName, String[] categories, String rootPathWritable, double alpha, boolean variant, int topN) throws IOException, PersistentClassifierException {
        super(classifierName, categories, rootPathWritable, topN);
        this.variant = variant;
        this.alpha = alpha;
    }

    public NaiveBayesClassifierLevelDBLaplacedImpl(String classifierName, String[] categories, String rootPathWritable, double alpha, boolean variant) throws IOException, PersistentClassifierException {
        super(classifierName, categories, rootPathWritable);
        this.variant = variant;
        this.alpha = alpha;
    }

    public NaiveBayesClassifierLevelDBLaplacedImpl(String classifierName, String[] categories, String rootPathWritable, int topN) throws IOException, PersistentClassifierException {
        this(classifierName, categories, rootPathWritable, ALPHA, VARIANT, topN);
    }

    public NaiveBayesClassifierLevelDBLaplacedImpl(String classifierName, String[] categories, String rootPathWritable) throws IOException, PersistentClassifierException {
        super(classifierName, categories, rootPathWritable);
        this.alpha = ALPHA;
        this.variant = VARIANT;
    }

    @Override
    public synchronized void learn(String category, Map<String, String> features, long weight) throws ClassifyException {
        ReadOptions ro = new ReadOptions();
        ro.snapshot(getDb().getSnapshot());
        WriteBatch batch = getDb().createWriteBatch();
        try {
            String pathGlobal = pathGlobal();
            batch.put(bytes(pathGlobal), Longs.toByteArray((getDb().get(bytes(pathGlobal), ro) == null ? weight : Longs.fromByteArray(getDb().get(bytes(pathGlobal), ro)) + weight)));
            String pathCategory = pathCategory(category);
            String pathGlobalCountCategories = pathGlobalCountCategories();
            if (getDb().get(bytes(pathCategory), ro) == null) {
                batch.put(bytes(pathCategory), Longs.toByteArray(weight));
                // increment the count
                batch.put(bytes(pathGlobalCountCategories), Longs.toByteArray((getDb().get(bytes(pathGlobalCountCategories), ro) == null ? 1 : Longs.fromByteArray(getDb().get(bytes(pathGlobalCountCategories), ro)) + 1)));
            } else {
                batch.put(bytes(pathCategory), Longs.toByteArray(Longs.fromByteArray(getDb().get(bytes(pathCategory), ro)) + weight));
            }
            for (Map.Entry<String, String> feature : features.entrySet()) {
                String pathFeatureKey = pathFeatureKey(feature.getKey());
                batch.put(bytes(pathFeatureKey), Longs.toByteArray((getDb().get(bytes(pathFeatureKey), ro) == null ? weight : Longs.fromByteArray(getDb().get(bytes(pathFeatureKey), ro)) + weight)));
                String pathCategoryFeatureKey = pathCategoryFeatureKey(category, feature.getKey());
                batch.put(bytes(pathCategoryFeatureKey), Longs.toByteArray((getDb().get(bytes(pathCategoryFeatureKey), ro) == null ? weight : Longs.fromByteArray(getDb().get(bytes(pathCategoryFeatureKey), ro)) + weight)));
                String pathCategoryFeatureKeyValue = pathCategoryFeatureKeyValue(category, feature.getKey(), feature.getValue());
                String pathFeatureKeyValue = pathFeatureKeyValue(feature.getKey(), feature.getValue());
                if (getDb().get(bytes(pathFeatureKeyValue), ro) == null) {
                    batch.put(bytes(pathFeatureKeyValue), Longs.toByteArray(weight));
                    // increment the count
                    String pathFeatureKeyCountValueTypes = pathFeatureKeyCountValueTypes(feature.getKey());
                    batch.put(bytes(pathFeatureKeyCountValueTypes), Longs.toByteArray((getDb().get(bytes(pathFeatureKeyCountValueTypes), ro) == null ? 1 : Longs.fromByteArray(getDb().get(bytes(pathFeatureKeyCountValueTypes), ro)) + 1)));
                } else {
                    batch.put(bytes(pathFeatureKeyValue), Longs.toByteArray(Longs.fromByteArray(getDb().get(bytes(pathFeatureKeyValue), ro)) + weight));
                }
                batch.put(bytes(pathCategoryFeatureKeyValue), Longs.toByteArray((getDb().get(bytes(pathCategoryFeatureKeyValue), ro) == null ? weight : Longs.fromByteArray(getDb().get(bytes(pathCategoryFeatureKeyValue), ro)) + weight)));
            }
            getDb().write(batch);
        } finally {
            try {
                // Make sure you close the batch to avoid resource leaks.
                batch.close();
            } catch (IOException ex) {
                throw new ClassifyException(ex);
            }
            try {
                // Make sure you close the batch to avoid resource leaks.
                ro.snapshot().close();
            } catch (IOException ex) {
                throw new ClassifyException(ex);
            }
        }
    }

    @Override
    public IClassification classify(Map<String, String> features, final boolean explain) throws ClassifyException {
        Map<String, Long> explanation = null;
        if (explain) {
            explanation = new HashMap();
        }
        ReadOptions ro = new ReadOptions();
        ro.snapshot(getDb().getSnapshot());
        try {
            String pathGlobal = pathGlobal();
            String pathGlobalCountCategories = pathGlobalCountCategories();
            long globalCount = (getDb().get(bytes(pathGlobal), ro) == null ? 0 : Longs.fromByteArray(getDb().get(bytes(pathGlobal), ro)));
            if (explain) {
                explanation.put(pathGlobal, globalCount);
            }
            long globalCountCategories = (getDb().get(bytes(pathGlobalCountCategories), ro) == null ? 0 : Longs.fromByteArray(getDb().get(bytes(pathGlobalCountCategories), ro)));
            if (explain) {
                explanation.put(pathGlobalCountCategories, globalCountCategories);
            }
            double[] likelyhood = new double[getCategories().length];
            double likelyhoodTot = 0;
            for (int i = 0; i < getCategories().length; i++) {
                String category = getCategories()[i];
                String pathCategory = pathCategory(category);
                long categoryCount = (getDb().get(bytes(pathCategory), ro) == null ? 0 : Longs.fromByteArray(getDb().get(bytes(pathCategory), ro)));
                double product = 1.0d;
                for (Map.Entry<String, String> feature : features.entrySet()) {
                    String pathFeatureKey = pathFeatureKey(feature.getKey());
                    //double featureCount = (getDb().get(ro, bytes(pathFeatureKey)) == null ? 0 : Longs.fromByteArray(getDb().get(ro, bytes(pathFeatureKey))));
                    long featureCount = (getDb().get(bytes(pathFeatureKey), ro) == null ? 0 : Longs.fromByteArray(getDb().get(bytes(pathFeatureKey), ro)));
                    if (explain) {
                        explanation.put(pathFeatureKey, featureCount);
                    }
                    if (featureCount > 0) {
                        String pathCategoryFeatureKey = pathCategoryFeatureKey(category, feature.getKey());
                        long categoryFeatureCount = (getDb().get(bytes(pathCategoryFeatureKey), ro) == null ? 0 : Longs.fromByteArray(getDb().get(bytes(pathCategoryFeatureKey), ro)));
                        if (explain) {
                            explanation.put(pathCategoryFeatureKey, categoryFeatureCount);
                        }
                        String pathFeatureKeyCountValueTypes = pathFeatureKeyCountValueTypes(feature.getKey());
                        long featureCountValueTypes = (getDb().get(bytes(pathFeatureKeyCountValueTypes), ro) == null ? 0 : Longs.fromByteArray(getDb().get(bytes(pathFeatureKeyCountValueTypes), ro)));
                        if (explain) {
                            explanation.put(pathFeatureKeyCountValueTypes, featureCountValueTypes);
                        }
                        String pathCategoryFeatureKeyValue = pathCategoryFeatureKeyValue(category, feature.getKey(), feature.getValue());
                        long categoryFeatureValueCount = (getDb().get(bytes(pathCategoryFeatureKeyValue), ro) == null ? 0 : Longs.fromByteArray(getDb().get(bytes(pathCategoryFeatureKeyValue), ro)));
                        if (explain) {
                            explanation.put(pathCategoryFeatureKeyValue, categoryFeatureValueCount);
                        }
                        double basicProbability = (categoryFeatureCount == 0 ? 0 : 1d * (categoryFeatureValueCount + alpha) / (categoryFeatureCount + featureCountValueTypes * alpha));
                        product *= basicProbability;
                    }
                }
                if (variant) {
                    likelyhood[i] = 1d * ((categoryCount + alpha) / (globalCount + globalCountCategories * alpha)) * product;
                } else {
                    likelyhood[i] = 1d * categoryCount / globalCount * product;
                }
                likelyhoodTot += likelyhood[i];
            }
            return new ClassificationImpl(likelihoodsToProbas(likelyhood, likelyhoodTot), explanation);
        } finally {
            try {
                // Make sure you close the snapshot to avoid resource leaks.
                ro.snapshot().close();
            } catch (IOException ex) {
                throw new ClassifyException(ex);
            }
        }
    }

}
