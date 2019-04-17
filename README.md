# Nyzo verifier

This is the source for the Nyzo verifier. This is the code that we run on all of our verifier instances (verifier0.nyzo.co through verifier9.nyzo.co).

If you are interested in this project, we recommend reading our white paper: https://nyzo.co/whitePaper. **This is a very different blockchain.** We feel that it's better in almost every way than other blockchains and cryptocurrencies, but it contains a lot of unproven technology, and we want everyone involved to understand exactly what Nyzo is and what Nyzo is not.

**Please note: the proof-of-diversity system requires that new verifiers be added to the cycle at a controlled rate. The length of time that a verifier must wait is related to the current cycle length and the number of verifiers waiting to join. As the cycle length increases, this may be a considerable amount of time. For instance, with a cycle length of 500, the minimum spacing between new verifiers will be approximately 2 hours. Please consult the Nyzo white paper for further details.**

To start your own verifier, we recommend creating a t3.small AWS instance with the latest Ubuntu LTS version, a 30GB EBS volume, port 9444 incoming open to the world for TCP only, and port 9446 incoming open to the world for UDP only. Also, open the SSH port to your IP address so you can access the instance. Then, when you have SSHed into the instance, run the following commands (enter each command separately):

```
sudo apt update
```

```
sudo apt install haveged -y
```

```
sudo apt install openjdk-8-jdk -y
```

```
sudo apt install supervisor -y
```

```
git clone https://github.com/n-y-z-o/nyzoVerifier.git
```

```
cd nyzoVerifier
```

```
./gradlew build
```

```
sudo mkdir -p /var/lib/nyzo/production
```

```
sudo cp trusted_entry_points /var/lib/nyzo/production
```

```
sudo cp nyzoVerifier.conf /etc/supervisor/conf.d/
```

If you want to give your verifier a nickname, which will be displayed in the mesh status at https://nyzo.co/mesh, run the following command, replacing VERIFIER_NICKNAME (but leaving the quotes) with the name you choose for your verifier:

```
sudo bash -c 'echo "VERIFIER_NICKNAME" > /var/lib/nyzo/production/nickname'
```

_This next step is not necessary for most verifiers. Only do it if you have a seed from an old verifier that you want to reuse. If you skip this step, a seed will be generated for you using a cryptographically secure random-number generator the first time the verifier starts._ **_The system is not designed for multiple verifiers to run with the same private key. If you do run multiple verifiers with the same private key, they will only be able to hold one space in the cycle, and your total earnings will be no more than if you were running only one verifier._** If you were running a verifier previously and want to reuse the private key from it, run the following command, replacing PRIVATE_KEY (but leaving the quotes) with your old key value (a 64-character hexadecimal string, with or without dashes):

```
sudo bash -c 'echo "PRIVATE_KEY" > /var/lib/nyzo/production/verifier_private_seed'
```

To start the verifier, run:

```
sudo supervisorctl reload
```

You may now see the private key that was generated for your verifier. You need to use this to access the tokens you will earn on your verifier. **_Keep a copy of this somewhere safe, and do not share it with anyone._** To see the key, run:

```
more /var/lib/nyzo/production/verifier_private_seed
```
