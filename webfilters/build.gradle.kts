plugins { id("com.android.asset-pack") }

assetPack {
  packName.set("webfilters")
  dynamicDelivery { deliveryType.set("fast-follow") }
}
