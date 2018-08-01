# Developer Sam Website Backend

The code of my own website backend that is open-sourced.

This is the repository for the frontend of [my website](https://developersam.com), written in 
Kotlin and hosted on Google Kubernetes Engine. You can freely use any part of my code while sticking
to the MIT license. A recommended approach is to take a look at the 
[KineticS](https://github.com/SamChou19815/kinetics) repo, which is a framework developed by 
myself to quickly bootstrap your backend, very suitable for hackathon use.

# Open Source Acknowledgement

It is impossible for this website to run without:

- [Spark (Apache 2.0)](https://github.com/perwendel/spark)
- [TypedStore (MIT)](https://github.com/SamChou19815/typed-store)
- [KineticS (MIT)](https://github.com/SamChou19815/kinetics)
- [SAMPL (MIT)](https://github.com/SamChou19815/sampl)
- [ChunkReader (MIT)](https://github.com/SamChou19815/chunk-reader)
- [TEN (MIT)](https://github.com/SamChou19815/ten)
- [OKaml-Lib(MIT)](https://github.com/SamChou19815/okaml-lib)

# Build Commands

- Deploy backend to my Google Kubernetes Engine: `bash cloud-push-backend.sh`
- Deploy cron job to my Google App Engine: `bash gae-cron/deploy.sh`
- Update Datastore indices on GCP: `./gradlew updateDatastoreIndices`
