import { Injectable } from '@angular/core';
import { AngularFireAuth } from 'angularfire2/auth';
import * as firebase from 'firebase/app';

@Injectable()
export class GoogleUserService {

  /**
   * Construct itself from the injected angular firebase auth.
   *
   * @param {AngularFireAuth} angularFireAuth the injected angular firebase auth.
   */
  constructor(private angularFireAuth: AngularFireAuth) { }

  /**
   * Perform a task after signed in with Google Account.
   *
   * @param {() => void} doTask the task to perform.
   */
  doTaskAfterSignedIn(doTask: () => void): void {
    this.angularFireAuth.authState.subscribe(userOptional => {
      if (userOptional === null) {
        // noinspection JSIgnoredPromiseFromCall
        this.angularFireAuth.auth.signInWithRedirect(new firebase.auth.GoogleAuthProvider());
        return;
      }
      userOptional.getIdToken(true).then(token => {
        localStorage.setItem('token', token);
        doTask();
      });
    });
  }

}