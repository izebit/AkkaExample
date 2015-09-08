package ru.izebit;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Класс,в который анмаршалится строки xml
 *
 * Created by Artem Konovalov on 9/8/15.
 */
@XmlRootElement(name = "offer")
public class Offer {
    private long id;
    private String pictureURL;
    private double price;
    private String checkResults;

    public Offer() {
        checkResults = "";
    }

    public long getId() {
        return id;
    }

    @XmlAttribute(name = "id")
    public void setId(long id) {
        this.id = id;
    }

    public String getPictureURL() {
        return pictureURL;
    }

    @XmlElement(name = "picture")
    public void setPictureURL(String pictureURL) {
        this.pictureURL = pictureURL;
    }

    public double getPrice() {
        return price;
    }

    @XmlElement(name = "price")
    public void setPrice(double price) {
        this.price = price;
    }

    public void setCheckResults(String checkResults) {
        this.checkResults = checkResults;
    }

    public String getCheckResults() {
        return checkResults;
    }

    @Override
    public String toString() {
        return id + " " + checkResults;
    }
}
