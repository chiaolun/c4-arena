#!/usr/bin/env python

from keras.models import Sequential
from keras.layers.core import Dense, Dropout, Activation
from keras.optimizers import RMSprop
import numpy as np
import random
import os.path

ncols = 7
nrows = 6
state_dim = ncols * nrows

# http://outlace.com/Reinforcement-Learning-Part-3/

def standardize(state,side):
    N = len(state)
    standard_state = np.zeros((N,2))
    for i, x in enumerate(state):
        if x == 0:
            continue
        elif x == side:
            standard_state[i,0] = 1.
        else:
            standard_state[i,1] = 1.
    return standard_state.reshape(1,state_dim*2)

def valid_columns(state):
    return [j for j in range(ncols)
            if any(all([state[0, 2 * i + k] == 0 for k in range(2)])
                   for i in range(j * nrows, (j + 1) * nrows))]

class NeuralQ():
    def __init__(self, epsilon = 0.01, gamma = 1., save_interval = 500):
        self.epsilon = epsilon
        self.gamma = gamma
        self.save_interval = save_interval
        self.epoch = 0
        self.replay = []
        self.memory_size = 200
        self.batch_size = 100

        self.models = {}
        for side in [1,2]:
            model = Sequential()
            model.add(Dense(60, init='lecun_uniform', input_shape=(state_dim*2,)))
            model.add(Activation('relu'))
            # model.add(Dropout(0.5))

            model.add(Dense(ncols, init='lecun_uniform'))
            model.add(Activation('linear')) #linear output so we can have range of real-valued outputs

            rms = RMSprop()
            model.compile(loss='mse', optimizer=rms)

            if os.path.isfile("model_{side}.dat".format(side=side)):
                model.load_weights("model_{side}.dat".format(side=side))

            self.models[side] = model

    def get_move(self, state, side):
        model = self.models[side]
        gamma = self.gamma
        epsilon = self.epsilon
        memory_size = self.memory_size
        batch_size = self.batch_size

        state = standardize(state,side)
        state = np.array(state)
        #We are in state S
        #Let's run our Q function on S to get Q values for all possible actions
        qval = model.predict(state, batch_size=1)
        qval_allowed = np.empty(qval.shape)
        qval_allowed[:] = np.NAN
        valids = valid_columns(state)
        for i in valids:
            qval_allowed[0,i] = qval[0,i]

        if (random.random() < epsilon): #choose random action
            action = np.random.choice(valids)
        else:
            action = (np.nanargmax(qval_allowed))

        def observe_reward(reward=0, new_state=None):
            if new_state:
                new_state = standardize(new_state,side)
                new_state = np.array(new_state)

            self.replay.append((side, state, action, reward, new_state))

            if len(self.replay) <= memory_size:
                # Don't start training until you have enough samples
                return

            self.replay.pop(0)

            minibatch = random.sample(self.replay, batch_size)

            X_train = {1 : [], 2 : []}
            y_train = {1 : [], 2 : []}

            for side0, old_state, action0, reward, new_state in minibatch:
                model0 = self.models[side0]
                old_qval = model0.predict(old_state, batch_size=1)

                # This function observes the reward after the move chosen
                y = np.zeros((1,ncols))
                y[:] = old_qval[:]

                if new_state is None:
                    # Terminal state
                    update = reward
                else:
                    # Non-terminal state

                    #Get max_Q(S',a)
                    newQ = model0.predict(new_state, batch_size=1)
                    qval_allowed = np.empty(newQ.shape)
                    qval_allowed[:] = np.NAN
                    valids = valid_columns(old_state)
                    for i in valids:
                        qval_allowed[0,i] = newQ[0,i]
                    maxQ = np.nanmax(qval_allowed)

                    update = (reward + (gamma * maxQ))

                y[0][action0] = update #target output
                X_train[side0].append(old_state.reshape(state_dim*2,))
                y_train[side0].append(y.reshape(ncols,))

            for side0 in [1,2]:
                model0 = self.models[side0]
                X_train0 = np.array(X_train[side0])
                y_train0 = np.array(y_train[side0])
                model0.fit(X_train0, y_train0, batch_size=len(X_train0),
                           nb_epoch=1, verbose=1)

            self.epoch += 1
            if self.epoch % self.save_interval == 0:
                for side0 in [1,2]:
                    (self
                     .models[side0]
                     .save_weights("model_{side}.dat".format(side=side0),
                                   overwrite=True))

        return action, observe_reward
