package com.fzu.addressbook.dto;

/**
 * 只修改收藏状态的请求体（1.1 收藏联系人）。
 */
public class FavoriteRequest {

    private boolean favorite;

    public FavoriteRequest() {
    }

    public FavoriteRequest(boolean favorite) {
        this.favorite = favorite;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }
}
