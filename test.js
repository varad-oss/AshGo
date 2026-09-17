const admin = require('firebase-admin');
admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  databaseURL: 'https://ash-safar-tracker-1787591514-default-rtdb.firebaseio.com'
});
const db = admin.database();
db.ref('chat').limitToLast(1).once('value', (snap) => {
  console.log(snap.val());
  process.exit(0);
});
