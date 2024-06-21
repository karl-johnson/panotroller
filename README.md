# panotroller
CAD files and software software stack for Bluetooth control of panoramic tripod head, including Android app and Arduino code. This project has reached a state where I am regularly using this device to take [10+ gigapixel panoramas](https://ka.rljohnson.net/panoramas/). You can read more about it [here](https://ka.rljohnson.net/projects/20201201-panoramic-head/20221025/).

[Legacy spreadsheet of TODO items.](https://docs.google.com/spreadsheets/d/1RmgUe2ZGkh_iMmGH30a_QDg2gMP9cVXYK8alv1t5Vuo/edit?usp=sharing)

I am done with this project, but if others decide to make something similar, a few major issues/recommendations regarding my design:
- Stainless steel is very sturdy but is likely overkill here. You could probably make something lighter and still sufficiently strong using aluminum extrusion.
- The up-down slide adjustment to balance the camera works, but it is sticky and if you accidentally loosen both screws, it is possible for the slide to completely fall off - taking your camera with it! At the very least, it would be good to add a stopper pin or similar to prevent this.
- Stepper motors are easy and make the hardware/firmware simple, but are heavy and inefficient for the battery. Brushless motors with a simple, light gearbox would probably be the optimal solution.
- This version is missing encoders. Adding a simple hall effect encoder like the AS5047 series would be very beneficial for this device.
- Use shielded cable for the motor wires. Only recently I noticed on a spectrum analyzer that the stepper motor drivers put out a LOT of switching noise, which probably is the reason why the bluetooth connection from the phone to the device is sometimes flaky.
- Related to the previous note - the current design has the HC-05 bluetooth module antenna directly against a stepper motor. This is probably also not a good idea for EMI, and it would be good to relocate the bluetooth antenna or entire module.
