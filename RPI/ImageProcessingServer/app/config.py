import os

# path to model and label binarizer
MODEL_PATH = "image_detector_new2.h5"
ENCODER_PATH = "label_encoder_new2.pickle"

# number of max proposals used when running selective search
MAX_PROPOSALS_INFER_NO = 150

# input dimensions to the network
INPUT_DIMENSIONS = (224, 224)

# minimum probability required for a positive prediction
MIN_PROB = 0.85