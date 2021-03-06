package common;

import java.util.List;
import java.util.ArrayList;


public class Sentence {
  int id;
  String label;
  public List<Integer> words;
  int sentLength;

  Sentence() {
    this.words = new ArrayList<Integer>();
    this.sentLength = 0;
  }

  // returns the ith word id
  public int get(int i) {
    assert (i<this.getSize());
    return this.words.get(i);
  }

  public void setId(int _id) {
    this.id = _id;
  }

  public int id() {
    return this.id;
  }

  public void setLabel(String _label) {
    this.label = _label;
  }

  public String label() {
    return this.label;
  }

  public void addWord(Integer t) {
    words.add(t);
    this.sentLength++;
  }

  public int getSize() {
    return this.words.size();
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();
    for(int word: words)
      sb.append(word+ " ");

    return sb.toString();
  }
}
