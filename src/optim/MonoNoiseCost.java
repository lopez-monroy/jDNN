package optim;

import models.Model;
import common.Sentence;
import common.Datum;
//import org.jblas.DoubleMatrix;
import math.DMath;
import math.DMatrix;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import java.util.List;

public class MonoNoiseCost {
  Model model;
  double cost;
  DMatrix grads;
  Lock lock;

  public MonoNoiseCost(Model _model) {
    this.model = _model;
    this.lock = new ReentrantLock();
    this.cost = 0.0;
    this.grads = DMath.createZerosMatrix(1, this.model.getThetaSize());
  }

  public double getCost() {
    return this.cost;
  }

  public DMatrix getGrads() {
    return this.grads;
  }
  public void computeCost(Datum d) {
    DMatrix s1_root = this.model.fProp(d.getData());
    DMatrix s2_root = this.model.fProp(d.getPos());
    int nSamples = d.getNegSampleSize();
    List<Sentence> neg = d.getNeg();
    double unitError = 0.0;
    for(int i=0; i<nSamples; i++) {
      DMatrix s3_root = this.model.fProp(neg.get(i));

      // 1/2*(A-N)^2 - 1/2*(A-B)^2
      unitError += -(0.5*s1_root.squaredDistance(s2_root))+(0.5*s1_root.squaredDistance(s3_root));
    }
    unitError = unitError/nSamples;
    lock.lock(); 
    {
      this.cost += unitError;
    }
    lock.unlock();
  }

  public void computeGrad(Datum d) {
    int nSamples = (d.getNegSampleSize()>0)?d.getNegSampleSize():1;
    List<Sentence> neg = d.getNeg();
    
    DMatrix unitGrads = DMath.createZerosMatrix(1, this.model.getThetaSize());

/*    for(int i=0; i<nSamples; i++) {
      // df/dA = (A-N) - (A-B)
      unitGrads.addi(this.model.bProp(d.getData(), neg.get(i)));
      unitGrads.subi(this.model.bProp(d.getData(), d.getPos()));

      // df/dN = (N-A)
      unitGrads.addi(this.model.bProp(neg.get(i), d.getData()));
      
      // df/dB = -(B-A)
      unitGrads.subi(this.model.bProp(d.getPos(), d.getData()));
    }
    unitGrads.muli(1.0/nSamples);
    lock.lock();
    {
      this.grads.addi(unitGrads);
    }
    lock.unlock();*/
       
    DMatrix A = this.model.fProp(d.getData());
    DMatrix B = this.model.fProp(d.getPos());

    DMatrix AB = A.sub(B);
    DMatrix BA = B.sub(A);

    for(int i=0; i<nSamples; i++) {
      // df/dA = (A-N) - (A-B)
      
      DMatrix N = this.model.fProp(neg.get(i));
      DMatrix AN = A.sub(N);
      DMatrix NA = N.sub(A);

      DMatrix tempGrad = this.model.bProp(d.getData(), AN);
      unitGrads.addi(tempGrad);
      tempGrad.close();

      tempGrad = this.model.bProp(d.getData(), AB);
      unitGrads.subi(tempGrad);
      tempGrad.close();

      // df/dB = -(B-A)
      tempGrad = this.model.bProp(d.getPos(), BA);
      unitGrads.subi(tempGrad);
      tempGrad.close();

      // df/dN =  (N-A)
      tempGrad = this.model.bProp(neg.get(i), NA);
      unitGrads.addi(tempGrad);
      tempGrad.close();
    }
    unitGrads.muli(1.0/nSamples);
    lock.lock();
    {
      this.grads.addi(unitGrads);
    }
    unitGrads.close();
    lock.unlock();
  }
}
