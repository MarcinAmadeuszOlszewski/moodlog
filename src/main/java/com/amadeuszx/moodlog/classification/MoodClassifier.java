package com.amadeuszx.moodlog.classification;

public interface MoodClassifier {

	MoodClassification classify(String entryText);
}
