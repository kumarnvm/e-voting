//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2016.02.25 at 03:58:45 PM GMT+03:00 
//


package uk.dsxt.voting.common.iso20022.jaxb;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for Document_MtngRsltDssmntn complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="Document_MtngRsltDssmntn">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="MtgRsltDssmntn" type="{}MeetingResultDisseminationV04"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Document_MtngRsltDssmntn", propOrder = {
    "mtgRsltDssmntn"
})
public class DocumentMtngRsltDssmntn {

    @XmlElement(name = "MtgRsltDssmntn", required = true)
    protected MeetingResultDisseminationV04 mtgRsltDssmntn;

    /**
     * Gets the value of the mtgRsltDssmntn property.
     * 
     * @return
     *     possible object is
     *     {@link MeetingResultDisseminationV04 }
     *     
     */
    public MeetingResultDisseminationV04 getMtgRsltDssmntn() {
        return mtgRsltDssmntn;
    }

    /**
     * Sets the value of the mtgRsltDssmntn property.
     * 
     * @param value
     *     allowed object is
     *     {@link MeetingResultDisseminationV04 }
     *     
     */
    public void setMtgRsltDssmntn(MeetingResultDisseminationV04 value) {
        this.mtgRsltDssmntn = value;
    }

}
