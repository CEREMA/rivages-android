package fr.cerema.rivages;

import java.util.ArrayList;

public class Segment {

    private ArrayList<Point> points;
    private String nmea ;
    private int limType;

    public Segment() {
        points=new ArrayList<>();
    }

    public ArrayList<Point> getPoints() {
        return points;
    }

    public void addPoint(Point point) {
        this.points.add(point);
    }

    public String getNmea() {
        return nmea;
    }

    public void setNmea(String nmea) {
        this.nmea = nmea;
    }

    public int getLimType() {
        return limType;
    }

    public void setLimType(int limType) {
        this.limType = limType;
    }
}
