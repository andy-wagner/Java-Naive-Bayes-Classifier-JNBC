/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.namsor.oss;

import com.namsor.oss.classify.bayes.ClassifyException;
import com.namsor.oss.classify.bayes.IClassification;
import com.namsor.oss.classify.bayes.NaiveBayesClassifierRocksDBImpl;
import com.namsor.oss.classify.bayes.NaiveBayesClassifierTransientImpl;
import com.namsor.oss.classify.bayes.NaiveBayesClassifierTransientImplV2;
import com.namsor.oss.classify.bayes.PersistentClassifierException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple test inspired by
 * http://ai.fon.bg.ac.rs/wp-content/uploads/2015/04/ML-Classification-NaiveBayes-2014.pdf
 *
 * @author elian
 */
public class MainSample3 {

    private static final String YES = "Yes";
    private static final String NO = "No";
    /**
     * Header table as per https://taylanbil.github.io/boostedNB or
     * http://ai.fon.bg.ac.rs/wp-content/uploads/2015/04/ML-Classification-NaiveBayes-2014.pdf
     */
    private static final String[] colName = {
        "outlook", "temp", "humidity", "wind", "play"
    };

    /**
     * Data table as per https://taylanbil.github.io/boostedNB or
     * http://ai.fon.bg.ac.rs/wp-content/uploads/2015/04/ML-Classification-NaiveBayes-2014.pdf
     */
    private static final String[][] data = {
        {"Sunny", "Hot", "High", "Weak", "No"},
        {"Sunny", "Hot", "High", "Strong", "No"},
        {"Overcast", "Hot", "High", "Weak", "Yes"},
        {"Rain", "Mild", "High", "Weak", "Yes"},
        {"Rain", "Cool", "Normal", "Weak", "Yes"},
        {"Rain", "Cool", "Normal", "Strong", "No"},
        {"Overcast", "Cool", "Normal", "Strong", "Yes"},
        {"Sunny", "Mild", "High", "Weak", "No"},
        {"Sunny", "Cool", "Normal", "Weak", "Yes"},
        {"Rain", "Mild", "Normal", "Weak", "Yes"},
        {"Sunny", "Mild", "Normal", "Strong", "Yes"},
        {"Overcast", "Mild", "High", "Strong", "Yes"},
        {"Overcast", "Hot", "Normal", "Weak", "Yes"},
        {"Rain", "Mild", "High", "Strong", "No"},};

    public static final void main(String[] args) {
        try {
            String[] cats = {YES, NO};
            // Create a new bayes classifier with string categories and string features.
            // INaiveBayesClassifier bayes1 = new NaiveBayesClassifierLevelDBImpl("sentiment", cats, ".", 100);
            NaiveBayesClassifierTransientImplV2 bayes = new NaiveBayesClassifierTransientImplV2("tennis", cats);
            //NaiveBayesClassifierRocksDBImpl bayes = new NaiveBayesClassifierRocksDBImpl("intro", cats, ".", 100);

// Examples to learn from.
            for (int i = 0; i < data.length; i++) {
                Set features = new HashSet();
                for (int j = 0; j < colName.length - 1; j++) {
                    String feature = colName[j] + "=" + data[i][j];
                    features.add(feature);
                }
                bayes.learn(data[i][colName.length - 1], features);
            }
            /**
             * Calculate the likelihood that: 
             * Outlook = sunny (0.22) Temperature = cool (0.33) Humidity = high (0.33) Windy = true (0.33) Play = yes (0.64)
             */

// Here are is X(B,S) to classify.
            String outlook = "outlook=Sunny";
            String temp = "temp=Cool";
            String humidity = "humidity=High";
            String wind = "wind=Strong";
            
            String[] features = {outlook, temp, humidity, wind};
            
            IClassification[] predict = bayes.classify(new HashSet(Arrays.asList(features)));
            StringWriter sw = new StringWriter();
            bayes.dumpDb(sw);
            System.out.println(sw);
            for (int i = 0; i < predict.length; i++) {
                System.out.println("P(" + predict[i].getCategory() + ")=" + predict[i].getProbability());
            }
        } catch (PersistentClassifierException ex) {
            Logger.getLogger(MainSample3.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassifyException ex) {
            Logger.getLogger(MainSample3.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(MainSample3.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}