package com.routeforge.addresssearch.data.di

import com.routeforge.addresssearch.data.PhotonGeocodeClient
import com.routeforge.addresssearch.domain.GeocodeClient
import org.koin.dsl.bind
import org.koin.dsl.module

val addressSearchDataModule =
    module {
        single { PhotonGeocodeClient(httpClient = get()) }.bind<GeocodeClient>()
    }
