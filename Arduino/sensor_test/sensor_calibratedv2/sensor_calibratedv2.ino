#include <RunningMedian.h>

const int MAX_SMALL_SENSOR = 80;
const int MAX_BIG_SENSOR = 150;
const int NUM_SAMPLES_MEDIAN = 15;

RunningMedian frontIR1_Median = RunningMedian(NUM_SAMPLES_MEDIAN);
RunningMedian frontIR2_Median = RunningMedian(NUM_SAMPLES_MEDIAN);
RunningMedian frontIR3_Median = RunningMedian(NUM_SAMPLES_MEDIAN);
RunningMedian rightIR1_Median = RunningMedian(NUM_SAMPLES_MEDIAN);
RunningMedian rightIR2_Median = RunningMedian(NUM_SAMPLES_MEDIAN);
RunningMedian leftIR_1_Median = RunningMedian(NUM_SAMPLES_MEDIAN);

double frontIR1_Value = 0, frontIR2_Value = 0, frontIR3_Value = 0;
double rightIR1_Value = 0, rightIR2_Value = 0, leftIR1_Value = 0;


void setup() {
  // put your setup code here, to run once:
  Serial.begin(9600);
  Serial.setTimeout(0);
  pinMode(A3, INPUT);
  pinMode(A0, INPUT);
  pinMode(A1, INPUT);
  pinMode(A2, INPUT);
  pinMode(A4, INPUT);
  pinMode(A5, INPUT);
}

void loop() {
  // put your main code here, to run repeatedly:
    Serial.print("Left cm: ");
    Serial.println(getLeftIR1());
    delay(1000);
   
}

double getFrontIR1() {
  for (int n = 0; n < NUM_SAMPLES_MEDIAN; n++) {
    readFrontSensor_1();
  }
  return frontIR1_Value;
}
double getFrontIR2() {
  for (int n = 0; n < NUM_SAMPLES_MEDIAN; n++) {
    readFrontSensor_2();
  }
  return frontIR2_Value;
}
double getFrontIR3() {
  for (int n = 0; n < NUM_SAMPLES_MEDIAN; n++) {
    readFrontSensor_3();
  }
  return frontIR3_Value;
}
double getRightIR1() {
  for (int n = 0; n < NUM_SAMPLES_MEDIAN; n++) {
    readRightSensor_1();
  }
  return rightIR1_Value;
}
double getRightIR2() {
  for (int n = 0; n < NUM_SAMPLES_MEDIAN; n++) {
    readRightSensor_2();
  }
  return rightIR2_Value;
}
double getLeftIR1() {
  for (int n = 0; n < NUM_SAMPLES_MEDIAN; n++) {
    readLeftSensor_1();
  }
  return leftIR1_Value;
}


void readFrontSensor_1() {
  double irDistance = 6110.1/analogRead(A3) - 2.7993;//Front left
//  Serial.println(irDistance + 9);
  frontIR1_Median.add(irDistance);
  if (frontIR1_Median.getCount() >= NUM_SAMPLES_MEDIAN) {
    if (abs(frontIR1_Median.getHighest() - frontIR1_Median.getLowest()) > 40) {
      frontIR1_Value = MAX_SMALL_SENSOR;
    } else {
      frontIR1_Value = frontIR1_Median.getMedian();
    }
  }
}

void readFrontSensor_2() {
  double irDistance = 5759.7/analogRead(A1) - 1.1496;//Middle
  frontIR2_Median.add(irDistance);
  if (frontIR2_Median.getCount() >= NUM_SAMPLES_MEDIAN) {
    if (abs(frontIR2_Median.getHighest() - frontIR2_Median.getLowest()) > 40) {
      frontIR2_Value = MAX_SMALL_SENSOR;
    } else {
      frontIR2_Value = frontIR2_Median.getMedian();
    }
  }
}

void readFrontSensor_3() {
  double irDistance = 7108.6/analogRead(A0) - 5.7032;//Front right
  frontIR3_Median.add(irDistance);
  if (frontIR3_Median.getCount() >= NUM_SAMPLES_MEDIAN) {
    if (abs(frontIR3_Median.getHighest() - frontIR3_Median.getLowest()) > 40) {
      frontIR3_Value = MAX_SMALL_SENSOR;
    } else {
      frontIR3_Value = frontIR3_Median.getMedian();
    }
  }
}

void readRightSensor_1() {
  double irDistance = 5631.1/analogRead(A2) - 2.0624;//right back
  rightIR1_Median.add(irDistance);
  if (rightIR1_Median.getCount() >= NUM_SAMPLES_MEDIAN) {
    if (abs(rightIR1_Median.getHighest() - rightIR1_Median.getLowest()) > 40) {
      rightIR1_Value = MAX_SMALL_SENSOR;
    } else {
      rightIR1_Value = rightIR1_Median.getMedian();
    }
  }
}

void readRightSensor_2() {
  double irDistance = 6262.7/analogRead(A5) - 3.1644;//right front
  rightIR2_Median.add(irDistance);
  if (rightIR2_Median.getCount() >= NUM_SAMPLES_MEDIAN) {
    if (abs(rightIR2_Median.getHighest() - rightIR2_Median.getLowest()) > 40) {
      rightIR2_Value = MAX_SMALL_SENSOR;
    } else {
      rightIR2_Value = rightIR2_Median.getMedian();
    }
  }
}

void readLeftSensor_1() {
  double irDistance = 15743/analogRead(A4) - 11.022;//Long Range
  leftIR_1_Median.add(irDistance);
  if (leftIR_1_Median.getCount() >= NUM_SAMPLES_MEDIAN) {
    if (abs(leftIR_1_Median.getHighest() - leftIR_1_Median.getLowest()) > 40) {
      leftIR1_Value = MAX_BIG_SENSOR;
    } else {
      leftIR1_Value = leftIR_1_Median.getMedian();
    }
  }
}
