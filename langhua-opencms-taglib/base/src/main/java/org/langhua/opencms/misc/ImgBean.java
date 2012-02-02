package org.langhua.opencms.misc;

public class ImgBean {
	private String name;
	private String fUrl;
	private String imgUrl;
	private String sIWidth;
	private String sIHeight;
	private String text;

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getFUrl() {
		return fUrl;
	}

	public void setFUrl(String url) {
		fUrl = url;
	}

	public String getImgUrl() {
		return imgUrl;
	}

	public void setImgUrl(String imgUrl) {
		this.imgUrl = imgUrl;
	}

	public String getSIWidth() {
		return sIWidth;
	}

	public void setSIWidth(String weight) {
		sIWidth = weight;
	}

	public String getSIHeight() {
		return sIHeight;
	}

	public void setSIHeight(String height) {
		sIHeight = height;
	}

}
