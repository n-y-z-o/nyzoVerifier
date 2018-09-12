# Nyzo verifier

This is the source for the Nyzo verifier. This is the code that we run on all of our verifier instances (verifier0.nyzo.co through verifier9.nyzo.co).

To start your own verifier, we recommend creating a t3.micro AWS instance with the latest Ubuntu LTS version, a 30GB EBS volume, and port 9444 open to the world. Also open the SSH port to your IP address so you can access the instance. Then, when you have SSHed into the instance, run the following commands:

```
sudo apt update
sudo apt install haveged -y
sudo apt install default-jdk -y
sudo apt install supervisor -y
git clone https://github.com/n-y-z-o/nyzoVerifier.git
cd nyzoVerifier
./gradlew build
sudo mkdir -p /var/lib/nyzo/production
sudo cp trusted_entry_points /var/lib/nyzo/production
sudo cp nyzoVerifier.conf /etc/supervisor/conf.d/
```

If you want to give your verifier a nickname, which will be displayed in the mesh status at https://nyzo.co/mesh, run the following command, replacing VERIFIER_NICKNAME (but leaving the quotes) with the name you choose for your verifier:

```
sudo bash -c 'echo "VERIFIER_NICKNAME" > /var/lib/nyzo/production/nickname'
```

If you were running a verifier previously and want to reuse the private key from it, run the following command, replacing PRIVATE_KEY (but leaving the quotes) with your old key value (a 64-character hexadecimal string, with or without dashes):

```
sudo bash -c 'echo "PRIVATE_KEY" > /var/lib/nyzo/production/verifier_private_seed'
```

To start the verifier, run:

```
sudo supervisorctl reload
```

You may now see the private key that was generated for your verifier. You need to use this to access the tokens you will earn on your verifier. **_Keep a copy of this somewhere safe, and do not share it with anyone._** To see the key, run:

```
more /var/lib/nyzo/verifier_private_seed
```
