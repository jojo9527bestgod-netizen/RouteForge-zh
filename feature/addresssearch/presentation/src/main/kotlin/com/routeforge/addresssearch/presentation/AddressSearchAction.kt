package com.routeforge.addresssearch.presentation

sealed interface AddressSearchAction {
    data class OnQueryChange(
        val value: String,
    ) : AddressSearchAction

    data object OnSubmit : AddressSearchAction

    data class OnSelectResult(
        val index: Int,
    ) : AddressSearchAction

    data object OnDismissChoice : AddressSearchAction

    data object OnChooseTeleport : AddressSearchAction

    data object OnChooseAddToRoute : AddressSearchAction

    data object OnChooseSaveAsFavorite : AddressSearchAction

    data class OnFavoriteNameChange(
        val value: String,
    ) : AddressSearchAction

    data object OnConfirmFavoriteName : AddressSearchAction

    data object OnDismissFavoriteName : AddressSearchAction
}
